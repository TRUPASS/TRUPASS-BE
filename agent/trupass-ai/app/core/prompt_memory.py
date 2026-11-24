from threading import Lock
from uuid import UUID, uuid4

from app.core.cache import TTLCache
from app.domain.models import PromptUpdate, SavedPrompts, TripInput


class PromptMemory:
    def __init__(self, ttl: int, capacity: int):
        self._cache = TTLCache(ttl, capacity)
        self._lock = Lock()

    def get(self, prompt_id: UUID) -> SavedPrompts:
        prompts = self._cache.get(str(prompt_id))
        if prompts is None:
            raise KeyError(prompt_id)
        return SavedPrompts(prompt_id=prompt_id, prompts=prompts)

    def save(self, update: PromptUpdate) -> SavedPrompts:
        with self._lock:
            prompt_id = update.prompt_id or uuid4()
            original = self.get(prompt_id).prompts if update.prompt_id else TripInput()
            changes = update.model_dump(exclude_unset=True, exclude={'prompt_id'})
            # An explicit null/blank clears that field. Omitted fields retain their value.
            prompts = original.model_copy(update={k: v or None for k, v in changes.items()})
            if not any((prompts.prompt, prompts.accommodation_prompt, prompts.purpose_prompt)):
                raise ValueError('여행·숙소·목적 중 한 가지 이상을 입력해 주세요.')
            if changes or not update.prompt_id:
                self._cache.put(str(prompt_id), prompts)
            return SavedPrompts(prompt_id=prompt_id, prompts=prompts)
