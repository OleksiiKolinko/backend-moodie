package org.cyberrealm.tech.muvio.service.impl;

import info.movito.themoviedbapi.model.movies.ReleaseDate;
import info.movito.themoviedbapi.model.movies.ReleaseInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.cyberrealm.tech.muvio.model.GenreEntity;
import org.cyberrealm.tech.muvio.model.Vibe;
import org.cyberrealm.tech.muvio.service.VibeService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VibeServiceImpl implements VibeService {
    private static final List<String> RATINGS = List.of("G", "PG", "PG-13", "R", "NC-17", "N-13",
            "NC16");
    private static final Map<GenreEntity, Map<Vibe, Integer>> GENRE_TO_VIBE_MAP = Map.ofEntries(
            Map.entry(GenreEntity.FAMILY, Map.of(Vibe.MAKE_ME_CHILL, 9, Vibe.MAKE_ME_FEEL_GOOD, 9,
                    Vibe.MAKE_ME_DREAM, 9)),
            Map.entry(GenreEntity.ROMANCE, Map.of(Vibe.MAKE_ME_DREAM, 8,
                    Vibe.MAKE_ME_FEEL_GOOD, 8)),
            Map.entry(GenreEntity.MUSIC, Map.of(Vibe.MAKE_ME_DREAM, 6, Vibe.MAKE_ME_FEEL_GOOD, 6)),
            Map.entry(GenreEntity.DOCUMENTARY, Map.of(Vibe.MAKE_ME_CURIOUS, 10)),
            Map.entry(GenreEntity.HISTORY, Map.of(Vibe.MAKE_ME_CURIOUS, 9)),
            Map.entry(GenreEntity.FANTASY, Map.of(Vibe.MAKE_ME_DREAM, 10,
                    Vibe.TAKE_ME_TO_ANOTHER_WORLD, 10)),
            Map.entry(GenreEntity.ANIMATION, Map.of(Vibe.MAKE_ME_CHILL, 9,
                    Vibe.MAKE_ME_FEEL_GOOD, 9)),
            Map.entry(GenreEntity.CRIME, Map.of(Vibe.KEEP_ME_ON_EDGE, 7)),
            Map.entry(GenreEntity.WESTERN, Map.of(Vibe.TAKE_ME_TO_ANOTHER_WORLD, 6,
                    Vibe.MAKE_ME_CURIOUS, 6)),
            Map.entry(GenreEntity.ACTION, Map.of(Vibe.KEEP_ME_ON_EDGE, 8, Vibe.BLOW_MY_MIND, 8)),
            Map.entry(GenreEntity.WAR, Map.of(Vibe.MAKE_ME_CURIOUS, 8, Vibe.BLOW_MY_MIND, 8)),
            Map.entry(GenreEntity.ADVENTURE, Map.of(Vibe.TAKE_ME_TO_ANOTHER_WORLD, 7,
                    Vibe.MAKE_ME_DREAM, 7, Vibe.BLOW_MY_MIND, 7)),
            Map.entry(GenreEntity.HORROR, Map.of(Vibe.SCARY_ME_SILLY, 10)),
            Map.entry(GenreEntity.COMEDY, Map.of(Vibe.MAKE_ME_CHILL, 10,
                    Vibe.MAKE_ME_FEEL_GOOD, 10)),
            Map.entry(GenreEntity.SCIENCE_FICTION, Map.of(Vibe.BLOW_MY_MIND, 10,
                    Vibe.TAKE_ME_TO_ANOTHER_WORLD, 10)),
            Map.entry(GenreEntity.THRILLER, Map.of(Vibe.KEEP_ME_ON_EDGE, 10)),
            Map.entry(GenreEntity.DRAMA, Map.of(Vibe.MAKE_ME_FEEL_GOOD, 6, Vibe.MAKE_ME_DREAM, 6,
                    Vibe.MAKE_ME_CURIOUS, 5)),
            Map.entry(GenreEntity.MYSTERY, Map.of(Vibe.MAKE_ME_CURIOUS, 7, Vibe.KEEP_ME_ON_EDGE, 7))
    );

    private static final Map<String, Map<Vibe, Integer>> RATING_TO_VIBE_MAP = Map.ofEntries(
            Map.entry("G", Map.of(Vibe.MAKE_ME_CHILL, 9, Vibe.MAKE_ME_FEEL_GOOD, 9,
                    Vibe.MAKE_ME_DREAM, 7)),
            Map.entry("PG", Map.of(Vibe.MAKE_ME_CHILL, 9, Vibe.MAKE_ME_FEEL_GOOD, 9,
                    Vibe.MAKE_ME_DREAM, 7)),
            Map.entry("NC-17", Map.of(Vibe.SCARY_ME_SILLY, 5, Vibe.KEEP_ME_ON_EDGE, 5,
                    Vibe.BLOW_MY_MIND, 5, Vibe.MAKE_ME_CURIOUS, 6)),
            Map.entry("R", Map.of(Vibe.BLOW_MY_MIND, 6, Vibe.KEEP_ME_ON_EDGE, 6,
                    Vibe.MAKE_ME_CURIOUS, 6)),
            Map.entry("PG-13", Map.of(Vibe.MAKE_ME_FEEL_GOOD, 8, Vibe.BLOW_MY_MIND, 8,
                    Vibe.MAKE_ME_DREAM, 8, Vibe.MAKE_ME_CURIOUS, 5)),
            Map.entry("N-13", Map.of(Vibe.TAKE_ME_TO_ANOTHER_WORLD, 7, Vibe.MAKE_ME_DREAM, 6,
                    Vibe.SCARY_ME_SILLY, 6)),
            Map.entry("NC16", Map.of(Vibe.BLOW_MY_MIND, 9, Vibe.KEEP_ME_ON_EDGE, 9,
                    Vibe.SCARY_ME_SILLY, 9))
    );

    @Override
    public Set<Vibe> getVibes(List<ReleaseInfo> releaseInfo, Set<GenreEntity> genresMdb) {
        Map<Vibe, Integer> vibeCount = calculateVibesFromGenres(genresMdb);
        return collectVibes(addVibesFromRatings(releaseInfo, vibeCount));
    }

    private Set<Vibe> collectVibes(Map<Vibe, Integer> vibeCount) {
        return vibeCount.entrySet().stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())))
                .entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(Set.of());
    }

    private Map<Vibe, Integer> calculateVibesFromGenres(Set<GenreEntity> genresMdb) {
        return genresMdb.stream()
                .flatMap(genreDb -> GENRE_TO_VIBE_MAP.getOrDefault(genreDb,
                        Map.of()).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));
    }

    private Map<Vibe, Integer> addVibesFromRatings(
            List<ReleaseInfo> releaseInfo, Map<Vibe, Integer> vibeCount) {
        releaseInfo.stream()
                .flatMap(release -> release.getReleaseDates()
                        .stream()
                        .map(ReleaseDate::getCertification))
                .filter(RATINGS::contains)
                .forEach(rating -> RATING_TO_VIBE_MAP.getOrDefault(rating, Map.of())
                        .forEach((vibe, score) -> vibeCount.merge(vibe, score, Integer::sum)));
        return vibeCount;
    }
}
