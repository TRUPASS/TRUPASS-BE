"""후보 목록을 받아 Gemini 한 번으로 추천 조합을 고른다. 금액 계산은 하지 않는다(core 담당)."""
import hashlib
import json
from pathlib import Path

from app.core.cache import TTLCache
from app.domain.recommend import Recommendation, RecommendRequest

PROMPT_PATH = Path(__file__).resolve().parents[1] / 'prompts' / 'recommend_combinations.md'
SYSTEM_INSTRUCTION = PROMPT_PATH.read_text(encoding='utf-8')


class InvalidRecommendation(Exception):
    """Gemini가 후보에 없는 key만 골랐을 때."""


class GeminiRecommender:
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

    def recommend(self, request: RecommendRequest) -> tuple[Recommendation, bool, int | None, int | None]:
        payload = request.model_dump(mode='json', by_alias=True, exclude_none=True)
        cache_key = hashlib.sha256(
            json.dumps([payload, self._model], sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached.model_copy(deep=True), False, 0, 0
        if not self._key:
            raise RuntimeError('GEMINI_API_KEY is not configured')

        user_text = json.dumps(payload, ensure_ascii=False, separators=(',', ':'))
        raw, prompt_tokens, output_tokens = self._generate(user_text)
        recommendation = self.keep_valid(raw, request)
        if not recommendation.options:
            raise InvalidRecommendation('no option refers to known candidates')
        self._cache.put(cache_key, recommendation.model_copy(deep=True))
        return recommendation, True, prompt_tokens, output_tokens

    @staticmethod
    def keep_valid(recommendation: Recommendation, request: RecommendRequest) -> Recommendation:
        """후보에 없는 key, 같은 조합 반복을 버리고 max_options개까지만 남긴다."""
        flight_keys = {f.key for f in request.flights}
        stay_keys = {s.key for s in request.stays}
        seen: set[tuple[str, str]] = set()
        kept = []
        for option in recommendation.options:
            pair = (option.flight_key.strip(), option.stay_key.strip())
            if pair[0] not in flight_keys or pair[1] not in stay_keys or pair in seen:
                continue
            seen.add(pair)
            kept.append(option.model_copy(update={'flight_key': pair[0], 'stay_key': pair[1]}))
            if len(kept) == request.max_options:
                break
        return Recommendation(summary=recommendation.summary, options=kept)

    def _generate(self, user_text: str) -> tuple[Recommendation, int | None, int | None]:
        from google.genai import types
        response = self._get_client().models.generate_content(
            model=self._model,
            contents=user_text,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                response_mime_type='application/json',
                response_schema=Recommendation,
                temperature=0.2,
                max_output_tokens=2048,
            ),
        )
        if response.parsed is not None:
            recommendation = Recommendation.model_validate(response.parsed)
        elif response.text:
            recommendation = Recommendation.model_validate_json(response.text)
        else:
            raise RuntimeError('Empty Gemini recommendation response')
        usage = response.usage_metadata
        return (recommendation,
                getattr(usage, 'prompt_token_count', None),
                getattr(usage, 'candidates_token_count', None))
