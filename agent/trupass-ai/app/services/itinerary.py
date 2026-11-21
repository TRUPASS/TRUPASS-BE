from app.domain.models import ItineraryDay, TripRequirements


COURSES = {
    'tokyo': {
        'art': [('우에노', ['우에노 공원', '국립서양미술관']),
                ('마루노우치·긴자', ['도쿄역 마루노우치 역사', '도쿄국제포럼']),
                ('롯폰기', ['국립신미술관', '도쿄 미드타운'])],
        'food': [('츠키지', ['츠키지 장외시장']), ('아사쿠사', ['나카미세 거리']), ('긴자', ['긴자 산책'])],
        'general': [('아사쿠사', ['센소지', '스미다강 산책']),
                    ('시부야·하라주쿠', ['메이지진구', '시부야 스크램블 교차로']),
                    ('마루노우치', ['도쿄역 마루노우치 역사'])],
    },
    'osaka': {
        'art': [('나카노시마', ['오사카 나카노시마 미술관', '오사카시 중앙공회당']),
                ('덴노지', ['오사카 시립 미술관'])],
        'food': [('난바', ['구로몬 시장', '도톤보리']), ('신세카이', ['잔잔요코초'])],
        'general': [('오사카성 일대', ['오사카성 공원']), ('난바', ['도톤보리', '난바 산책'])],
    },
    'fukuoka': {
        'art': [('오호리', ['후쿠오카시 미술관', '오호리 공원']),
                ('하카타', ['후쿠오카 아시아미술관'])],
        'food': [('하카타', ['하카타역 식당가']), ('나카스', ['나카스 야타이 거리'])],
        'general': [('오호리', ['오호리 공원']), ('하카타', ['구시다 신사', '하카타 구시가지'])],
    },
}
CITY_NAMES = {'도쿄': 'tokyo', '동경': 'tokyo', '오사카': 'osaka', '후쿠오카': 'fukuoka'}
THEMES = {
    'art': ('미술', '건축', '전시', 'museum', 'architecture', 'art gallery'),
    'food': ('맛집', '음식', '미식', '먹거리', 'food', 'culinary'),
}


def recommend_itinerary(req: TripRequirements) -> list[ItineraryDay]:
    if not (req.destination and req.departure_date and req.return_date):
        return []
    city = req.destination.strip().casefold()
    courses = COURSES.get(CITY_NAMES.get(city, city))
    if not courses:
        return []
    purpose = req.purpose.casefold()
    theme = next((name for name, terms in THEMES.items() if any(term in purpose for term in terms)), 'general')
    days = (req.return_date - req.departure_date).days + 1
    return [
        ItineraryDay(day=index, area=area, stops=stops)
        for index, (area, stops) in enumerate(courses[theme][:days], start=1)
    ]
