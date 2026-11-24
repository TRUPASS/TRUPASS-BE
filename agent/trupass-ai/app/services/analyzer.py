"""One compact structured-output Gemini call per new prompt; no chain-of-thought requested."""
import hashlib
import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo
from app.core.cache import TTLCache
from app.domain.models import TripInput, TripRequirements


PROMPT_PATH = Path(__file__).resolve().parents[1] / 'prompts' / 'extract_trip_requirements.md'
SYSTEM_INSTRUCTION = PROMPT_PATH.read_text(encoding='utf-8')


class GeminiAnalyzer:
    def __init__(self, api_key: str, model: str, cache: TTLCache):
        self._key = api_key
        self._model = model
        self._cache = cache
        self._client = None

    def _get_client(self):
        if self._client is None:
            from google import genai
            self._client = genai.Client(api_key=self._key)
        return self._client

    def analyze(self, request: TripInput) -> tuple[TripRequirements, bool, int | None, int | None]:
        payload = request.model_dump(mode='json')
        # Date in key prevents old relative-date interpretation across days.
        today = datetime.now(ZoneInfo("Asia/Seoul")).date().isoformat()
        cache_key = hashlib.sha256(json.dumps([today, payload, self._model], sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached.model_copy(deep=True), False, 0, 0
        if not self._key:
            raise RuntimeError('GEMINI_API_KEY is not configured')

        user_text = json.dumps({'today': today, **payload}, ensure_ascii=False, separators=(',', ':'))
        requirements, prompt_tokens, output_tokens = self._extract(user_text)
        if not requirements.origin and request.origin_hint:
            requirements.origin = request.origin_hint
        self._cache.put(cache_key, requirements.model_copy(deep=True))
        return requirements, True, prompt_tokens, output_tokens

    def _extract(self, user_text: str) -> tuple[TripRequirements, int | None, int | None]:
        from google.genai import types
        response = self._get_client().models.generate_content(
            model=self._model,
            contents=user_text,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                response_mime_type='application/json',
                response_schema=TripRequirements,
                temperature=0,
                max_output_tokens=600,
            ),
        )
        if response.parsed is not None:
            requirements = TripRequirements.model_validate(response.parsed)
        elif response.text:
            requirements = TripRequirements.model_validate_json(response.text)
        else:
            raise RuntimeError('Empty Gemini analysis response')
        usage = response.usage_metadata
        return (requirements,
                getattr(usage, 'prompt_token_count', None),
                getattr(usage, 'candidates_token_count', None))
