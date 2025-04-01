package org.cyberrealm.tech.muvio.service.impl;

import info.movito.themoviedbapi.model.core.Genre;
import info.movito.themoviedbapi.model.core.NamedIdElement;
import info.movito.themoviedbapi.model.keywords.Keyword;
import info.movito.themoviedbapi.model.movies.Cast;
import info.movito.themoviedbapi.model.movies.Credits;
import info.movito.themoviedbapi.model.movies.Crew;
import info.movito.themoviedbapi.model.movies.MovieDb;
import info.movito.themoviedbapi.model.tv.series.CreatedBy;
import info.movito.themoviedbapi.model.tv.series.TvSeriesDb;
import java.time.LocalTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.cyberrealm.tech.muvio.exception.MediaProcessingException;
import org.cyberrealm.tech.muvio.mapper.ActorMapper;
import org.cyberrealm.tech.muvio.mapper.GenreMapper;
import org.cyberrealm.tech.muvio.mapper.MediaMapper;
import org.cyberrealm.tech.muvio.mapper.ReviewMapper;
import org.cyberrealm.tech.muvio.model.Actor;
import org.cyberrealm.tech.muvio.model.GenreEntity;
import org.cyberrealm.tech.muvio.model.Media;
import org.cyberrealm.tech.muvio.model.Review;
import org.cyberrealm.tech.muvio.model.RoleActor;
import org.cyberrealm.tech.muvio.model.Type;
import org.cyberrealm.tech.muvio.repository.actors.ActorRepository;
import org.cyberrealm.tech.muvio.repository.media.MediaRepository;
import org.cyberrealm.tech.muvio.service.AwardService;
import org.cyberrealm.tech.muvio.service.CategoryService;
import org.cyberrealm.tech.muvio.service.MediaSyncService;
import org.cyberrealm.tech.muvio.service.TmDbService;
import org.cyberrealm.tech.muvio.service.TopListService;
import org.cyberrealm.tech.muvio.service.VibeService;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MediaSyncServiceImpl implements MediaSyncService, SmartLifecycle {
    private static final int SHORT_DURATION = 40;
    private static final String IMAGE_PATH = "https://image.tmdb.org/t/p/w500";
    private static final int LIMIT_THREADS =
            Math.min(20, Runtime.getRuntime().availableProcessors() * 2);
    private static final int BATCH_SIZE = 500;
    private static final int ZERO = 0;
    private static final int ONE = 1;
    private static final int LAST_PAGE = 500;
    private static final String REGION = "US";
    private static final String LANGUAGE = "en";
    private static final String DIRECTOR = "Director";
    private static final String PRODUCER = "Producer";
    private static final String DEFAULT_LANGUAGE = "null";
    private static final int FIRST_YEAR = 1920;
    private static final int TEN = 10;
    private static final int DEFAULT_SERIAL_DURATION = 30;
    private static final String TV = "TV";
    private static final String CRON_WEEKLY = "0 0 3 ? * MON";
    private static final int SLEEP_TIME = 250;
    private boolean isRunning;
    private final TmDbService tmdbService;
    private final MediaRepository mediaRepository;
    private final ActorRepository actorRepository;
    private final CategoryService categoryService;
    private final VibeService vibeService;
    private final GenreMapper genreMapper;
    private final MediaMapper mediaMapper;
    private final ActorMapper actorMapper;
    private final ReviewMapper reviewMapper;
    private final TopListService topListService;
    private final AwardService awardService;

    @Override
    public void importMedia(int fromPage, int toPage, String language, String location,
                            int currentYear, Set<String> imdbTop250, Set<String> winningMedia,
                            Map<Integer, Actor> actors, Map<String, Media> medias,
                            boolean isMovies) {
        try (final ForkJoinPool pool = new ForkJoinPool(LIMIT_THREADS)) {
            System.out.println("start " + LocalTime.now());
            final Set<Integer> movieList = isMovies
                    ? new HashSet<>(tmdbService.fetchPopularMovies(fromPage, toPage, language,
                    location, pool))
                    : new HashSet<>(tmdbService.fetchPopularTvSerials(fromPage, toPage, language,
                    location, pool));
            pool.submit(() -> movieList.parallelStream()
                    .filter(id -> !medias.containsKey((isMovies ? null : TV) + id))
                    .peek(id -> sleep()).forEach(id -> {
                        final Media movie = isMovies
                                ? createMovie(language, currentYear, id, imdbTop250,
                                winningMedia, actors)
                                : createTvSeries(language, currentYear, id, imdbTop250,
                                winningMedia, actors);
                        medias.put(movie.getId(), movie);
                    })).get();
            System.out.println("before filter " + LocalTime.now());
        } catch (InterruptedException | ExecutionException e) {
            throw new MediaProcessingException("Failed to process movie with thread pool", e);
        }
    }

    @Override
    public void importMediaByFilter(String language, int currentYear, Set<String> imdbTop250,
                                    Set<String> winningMedia, Map<String, Media> media,
                                    Map<Integer, Actor> actors, boolean isMovies) {
        try (ForkJoinPool pool = new ForkJoinPool(LIMIT_THREADS)) {
            final Set<Integer> ids = pool.submit(
                    () -> IntStream.rangeClosed(FIRST_YEAR, currentYear).parallel()
                            .peek(year -> sleep()).boxed()
                            .flatMap(year -> IntStream.iterate(
                                    ONE, page -> page <= LAST_PAGE, page -> page + ONE)
                                    .mapToObj(page -> {
                                        System.out.println("serial page " + page
                                                + " year " + year + "  " + LocalTime.now());
                                        final Set<Integer> filteredMovies = isMovies
                                                ? new HashSet<>(tmdbService
                                                .getFilteredMovies(year, page))
                                                : new HashSet<>(tmdbService
                                                .getFilteredTvShows(year, page));
                                        return filteredMovies.stream().filter(id -> !media
                                                        .containsKey((isMovies ? null : TV) + id))
                                        .collect(Collectors.toSet());
                                    }).takeWhile(set -> !set.isEmpty())).flatMap(Collection::stream)
                    .collect(Collectors.toSet())).get();
            pool.submit(() -> ids.parallelStream().peek(id -> sleep()).forEach(id -> {
                final Media newMedia = isMovies
                        ? createMovie(language, currentYear, id, imdbTop250, winningMedia, actors)
                        : createTvSeries(language, currentYear, id, imdbTop250,
                        winningMedia, actors);
                media.put(newMedia.getId(), newMedia);
            })).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new MediaProcessingException(
                    "Failed to process serials with thread pool in filter", e);
        }
    }

    @Override
    public void importByFindingTitles(String language, String region, int currentYear,
                                      Map<Integer, Actor> actors, Map<String, Media> medias,
                                      Set<String> imdbTop250,
                                      Set<String> winningMedia, boolean isMovies) {
        final Set<Integer> mediaId = new HashSet<>();
        findMediasIdsByTitles(language, region, imdbTop250, isMovies, mediaId);
        findMediasIdsByTitles(language, region, winningMedia, isMovies, mediaId);
        if (mediaId.isEmpty()) {
            return;
        }
        try (ForkJoinPool pool = new ForkJoinPool(LIMIT_THREADS)) {
            pool.submit(() -> mediaId.parallelStream()
                    .filter(id -> !medias.containsKey((isMovies ? null : TV) + id))
                    .peek(id -> sleep()).forEach(id -> {
                        final Media newMedia = isMovies
                                ? createMovie(language, currentYear, id, imdbTop250, winningMedia,
                                actors)
                                : createTvSeries(language, currentYear, id, imdbTop250,
                                winningMedia, actors);
                        medias.put(newMedia.getId(), newMedia);
                    })).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new MediaProcessingException(
                    "Failed to process create and save medias in import by finding titles", e);
        }
    }

    @Override
    public void deleteAll() {
        if (actorRepository != null) {
            actorRepository.deleteAll();
        }
        if (mediaRepository != null) {
            mediaRepository.deleteAll();
        }
    }

    @Override
    public void saveAll(Map<Integer, Actor> actors, Map<String, Media> medias) {
        List<Actor> actorList = new ArrayList<>(actors.values());
        for (int i = 0; i < actorList.size(); i += BATCH_SIZE) {
            int toIndex = Math.min(i + BATCH_SIZE, actorList.size());
            actorRepository.saveAll(actorList.subList(i, toIndex));
        }
        List<Media> mediaList = new ArrayList<>(medias.values());
        for (int i = 0; i < mediaList.size(); i += BATCH_SIZE) {
            int toIndex = Math.min(i + BATCH_SIZE, mediaList.size());
            mediaRepository.saveAll(mediaList.subList(i, toIndex));
        }
    }

    @Scheduled(cron = CRON_WEEKLY)
    @Override
    public void start() {
        final Map<Integer, Actor> actors = new ConcurrentHashMap<>();
        final Map<String, Media> medias = new ConcurrentHashMap<>();
        int currentYear = Year.now().getValue();
        final Set<String> imdbTop250Movies = awardService.getImdbTop250Movies();
        final Set<String> oscarWinningMovies = awardService.getOscarWinningMovies();
        final Set<String> imdbTop250TvShows = awardService.getImdbTop250TvShows();
        final Set<String> emmyWinningTvShows = awardService.getEmmyWinningTvShows();
        importMedia(ONE, LAST_PAGE, LANGUAGE, REGION, currentYear,
                imdbTop250Movies, oscarWinningMovies,
                actors, medias, true);
        importMedia(ONE, LAST_PAGE, LANGUAGE, REGION, currentYear,
                imdbTop250TvShows, emmyWinningTvShows,
                actors, medias, false);
        importByFindingTitles(LANGUAGE, REGION, currentYear, actors, medias, imdbTop250Movies,
                oscarWinningMovies, true);
        importByFindingTitles(LANGUAGE, REGION, currentYear, actors, medias, imdbTop250TvShows,
                emmyWinningTvShows, false);
        importMediaByFilter(LANGUAGE, currentYear, imdbTop250Movies, oscarWinningMovies, medias,
                actors, true);
        importMediaByFilter(LANGUAGE, currentYear, imdbTop250TvShows, emmyWinningTvShows, medias,
                actors, false);
        deleteAll();
        saveAll(actors, medias);
        isRunning = true;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getPhase() {
        return ONE;
    }

    private Media createMovie(String language, int currentYear,
                              Integer movieId,
                              Set<String> imdbTop250,
                              Set<String> oscarWinningMedia, Map<Integer, Actor> actors) {
        final MovieDb movieDb = tmdbService.fetchMovieDetails(movieId, language);
        final List<Keyword> keywords = tmdbService.fetchMovieKeywords(movieId)
                .getKeywords();
        final Credits credits = tmdbService.fetchMovieCredits(movieId, language);
        final Media media = mediaMapper.toEntity(movieDb);
        final Double voteAverage = media.getRating();
        final Integer voteCount = movieDb.getVoteCount();
        final Double popularity = movieDb.getPopularity();
        final String title = media.getTitle();
        media.setPosterPath(IMAGE_PATH + movieDb.getPosterPath());
        media.setTrailer(tmdbService.fetchMovieTrailer(movieId, language));
        media.setPhotos(tmdbService.fetchMoviePhotos(DEFAULT_LANGUAGE, movieId));
        media.setReleaseYear(getReleaseYear(movieDb.getReleaseDate(), currentYear));
        media.setDirector(getMovieDirector(credits.getCrew()));
        media.setActors(getMovieActors(credits.getCast(), actors));
        final Set<GenreEntity> genres = getGenres(movieDb.getGenres());
        media.setGenres(genres);
        media.setReviews(getReviews(() ->
                tmdbService.fetchMovieReviews(language, movieId)));
        media.setVibes(vibeService.getVibes(tmdbService.fetchTmDbMovieRatings(movieId), genres));
        media.setCategories(categoryService.putCategories(media.getOverview().toLowerCase(),
                keywords, voteAverage, voteCount, popularity, imdbTop250, title));
        media.setType(putType(media.getDuration()));
        media.setTopLists(topListService.putTopLists(keywords, voteAverage, voteCount, popularity,
                media.getReleaseYear(), oscarWinningMedia, title, movieDb.getBudget(),
                movieDb.getRevenue()));
        return media;
    }

    private Media createTvSeries(String language, int currentYear,
                                 Integer seriesId,
                                 Set<String> imdbTop250,
                                 Set<String> emmyWinningMedia, Map<Integer, Actor> actors) {
        final List<Keyword> keywords = tmdbService.fetchTvSerialsKeywords(seriesId)
                .getResults();
        final info.movito.themoviedbapi.model.tv.core.credits.Credits credits;
        final TvSeriesDb tvSeriesDb = tmdbService.fetchTvSerialsDetails(seriesId, language);
        final Media media = mediaMapper.toEntity(tvSeriesDb);
        credits = tmdbService.fetchTvSerialsCredits(seriesId, language);
        final Double voteAverage = media.getRating();
        final Integer voteCount = tvSeriesDb.getVoteCount();
        final Double popularity = tvSeriesDb.getPopularity();
        final String title = media.getTitle();
        media.setDuration(getDurations(tvSeriesDb));
        media.setPosterPath(IMAGE_PATH + tvSeriesDb.getPosterPath());
        media.setTrailer(tmdbService.fetchTvSerialsTrailer(seriesId, language));
        media.setPhotos(tmdbService.fetchTvSerialsPhotos(DEFAULT_LANGUAGE, seriesId));
        media.setReleaseYear(getReleaseYear(tvSeriesDb.getFirstAirDate(), currentYear));
        media.setDirector(getTvDirector(tvSeriesDb.getCreatedBy()));
        media.setActors(getTvActors(credits.getCast(), actors));
        final Set<GenreEntity> genres = getGenres(tvSeriesDb.getGenres());
        media.setGenres(genres);
        media.setReviews(getReviews(() ->
                tmdbService.fetchTvSerialsReviews(language, seriesId)));
        media.setCategories(categoryService.putCategories(media.getOverview().toLowerCase(),
                keywords, voteAverage, voteCount, popularity, imdbTop250, title));
        media.setTopLists(topListService.putTopListsForTvShow(keywords, voteAverage, voteCount,
                popularity, media.getReleaseYear(), emmyWinningMedia, title));
        media.setVibes(vibeService.getVibes(tmdbService.fetchTmDbTvRatings(seriesId), genres));
        media.setType(Type.TV_SHOW);
        return media;
    }

    private Integer getDurations(TvSeriesDb tvSeriesDb) {
        return tvSeriesDb.getEpisodeRunTime().stream()
                .findFirst()
                .orElse(DEFAULT_SERIAL_DURATION);
    }

    private Type putType(int duration) {
        if (duration < SHORT_DURATION && duration != ZERO) {
            return Type.SHORTS;
        } else {
            return Type.MOVIE;
        }
    }

    private List<Review> getReviews(
            Supplier<List<info.movito.themoviedbapi.model.core.Review>> reviewsSupplier
    ) {
        return reviewsSupplier.get().stream()
                .map(reviewMapper::toEntity)
                .toList();
    }

    private List<RoleActor> getMovieActors(List<Cast> casts, Map<Integer, Actor> actors) {
        return casts.stream().map(cast -> {
            final RoleActor roleActor = new RoleActor();
            roleActor.setRole(cast.getCharacter());
            roleActor.setActor(actors.computeIfAbsent(
                    cast.getId(), id -> actorMapper.toActorEntity(cast)));
            return roleActor;
        }).toList();
    }

    private List<RoleActor> getTvActors(
            List<info.movito.themoviedbapi.model.tv.core.credits.Cast> casts,
            Map<Integer, Actor> actors) {
        return casts.stream().map(cast -> {
            final RoleActor roleActor = new RoleActor();
            roleActor.setRole(cast.getCharacter());
            roleActor.setActor(actors.computeIfAbsent(cast.getId(),
                    id -> actorMapper.toActorEntity(cast)));
            return roleActor;
        }).toList();
    }

    private String getMovieDirector(List<Crew> crews) {
        return crews.stream().filter(crew -> crew.getJob().equalsIgnoreCase(DIRECTOR)
                || crew.getJob().equalsIgnoreCase(PRODUCER))
                .findFirst()
                .map(Crew::getName)
                .orElse(null);
    }

    private String getTvDirector(List<CreatedBy> creators) {
        return creators.stream()
                .map(NamedIdElement::getName)
                .findFirst()
                .orElse(null);
    }

    private Set<GenreEntity> getGenres(List<Genre> genres) {
        return genres.stream()
                .map(genreMapper::toGenreEntity)
                .collect(Collectors.toSet());
    }

    private Integer getReleaseYear(String releaseDate, int currentYear) {
        return Optional.ofNullable(releaseDate).filter(date -> date.length() == TEN)
                .map(date -> Integer.parseInt(date.substring(0, 4))).orElse(currentYear);
    }

    private void findMediasIdsByTitles(String language, String region, Set<String> titles,
                                       boolean isMovies, Set<Integer> mediaId) {
        if (!titles.isEmpty()) {
            try (ForkJoinPool pool = new ForkJoinPool(LIMIT_THREADS)) {
                pool.submit(() -> titles.parallelStream()
                        .map(title -> isMovies
                                ? tmdbService.searchMovies(title, language, region)
                                : tmdbService.searchTvSeries(title, language))
                        .filter(Optional::isPresent)
                        .forEach(id -> mediaId.add(id.get()))).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new MediaProcessingException(
                        "Failed to process find mediasIds by titles in thread pool", e);
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
            throw new MediaProcessingException("Failed to sleep", e);
        }
    }
}
