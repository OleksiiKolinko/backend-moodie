package org.cyberrealm.tech.muvio.service.impl;

import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbGenre;
import info.movito.themoviedbapi.TmdbMovies;
import info.movito.themoviedbapi.model.core.Genre;
import info.movito.themoviedbapi.model.core.Movie;
import info.movito.themoviedbapi.model.core.MovieResultsPage;
import info.movito.themoviedbapi.model.core.video.VideoResults;
import info.movito.themoviedbapi.model.movies.Cast;
import info.movito.themoviedbapi.model.movies.Credits;
import info.movito.themoviedbapi.model.movies.Crew;
import info.movito.themoviedbapi.model.movies.Images;
import info.movito.themoviedbapi.model.movies.MovieDb;
import info.movito.themoviedbapi.tools.TmdbException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.cyberrealm.tech.muvio.model.Actor;
import org.cyberrealm.tech.muvio.model.Duration;
import org.cyberrealm.tech.muvio.model.GenreEntity;
import org.cyberrealm.tech.muvio.model.Photo;
import org.cyberrealm.tech.muvio.model.Producer;
import org.cyberrealm.tech.muvio.model.Rating;
import org.cyberrealm.tech.muvio.model.Year;
import org.cyberrealm.tech.muvio.repository.actors.ActorRepository;
import org.cyberrealm.tech.muvio.repository.durations.DurationRepository;
import org.cyberrealm.tech.muvio.repository.genres.GenreRepository;
import org.cyberrealm.tech.muvio.repository.movies.MovieRepository;
import org.cyberrealm.tech.muvio.repository.photos.PhotoRepository;
import org.cyberrealm.tech.muvio.repository.producer.ProducerRepository;
import org.cyberrealm.tech.muvio.repository.ratings.RatingRepository;
import org.cyberrealm.tech.muvio.repository.years.YearRepository;
import org.cyberrealm.tech.muvio.service.TmdbService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TmdbApiServiceImpl implements TmdbService {
    private static final String YOUTUBE_PATH = "https://www.youtube.com/watch?v=";
    private static final String IMAGE_PATH = "https://image.tmdb.org/t/p/w500";
    private static final String PRODUCER = "Producer";
    private static final String DIRECTOR = "Director";
    private static final String TRAILER = "Trailer";
    private static final String TEASER = "Teaser";
    private final TmdbApi tmdbApi;
    private final PhotoRepository photoRepository;
    private final YearRepository yearRepository;
    private final RatingRepository ratingRepository;
    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final ProducerRepository producerRepository;
    private final ActorRepository actorRepository;
    private final DurationRepository durationRepository;

    @Override
    public void importMovies(int fromPage, int toPage, String language, String location) {
        deleteAll();
        loadGenres(language);
        final TmdbMovies tmdbMovies = tmdbApi.getMovies();
        for (int page = fromPage; page <= toPage; page++) {
            final MovieResultsPage movieResultsPage;
            try {
                movieResultsPage = tmdbApi.getMovieLists().getPopular(language, page, location);
            } catch (TmdbException e) {
                throw new RuntimeException("Failed to fetch popular movies");
            }
            final List<Movie> movies = movieResultsPage.getResults();
            for (Movie movie : movies) {
                final MovieDb movieDb;
                final int movieId = movie.getId();
                final Credits credits;
                final org.cyberrealm.tech.muvio.model.Movie movieMdb =
                        new org.cyberrealm.tech.muvio.model.Movie();
                try {
                    movieDb = tmdbApi.getMovies().getDetails(movieId, language);
                    movieMdb.setPhotos(getPhotos(tmdbMovies.getImages(movieId, language)));
                    movieMdb.setTrailer(getTrailer(tmdbMovies.getVideos(movieId, language)));
                    credits = tmdbMovies.getCredits(movieId, language);
                } catch (TmdbException e) {
                    throw new RuntimeException("Can't find movie or credits or video or images"
                            + " or keywords by movieId " + movieId);
                }
                movieMdb.setId(String.valueOf(movieId));
                movieMdb.setName(movieDb.getTitle());
                movieMdb.setPosterPath(getPosterPath(IMAGE_PATH + movieDb.getPosterPath()));
                movieMdb.setReleaseYear(getReleaseYear(movieDb.getReleaseDate()));
                movieMdb.setOverview(movieDb.getOverview());
                movieMdb.setRating(getRating(movieDb.getVoteAverage()));
                movieMdb.setGenres(getGenres(movieDb.getGenres()));
                movieMdb.setProducer(getProducer(credits.getCrew()));
                movieMdb.setActors(getActors(credits.getCast()));
                movieMdb.setDuration(getDuration(movieDb.getRuntime()));
                movieRepository.save(movieMdb);
            }
        }
    }

    private Set<Photo> getPhotos(Images images) {
        final Set<String> posters = images.getPosters().stream()
                .map(poster -> IMAGE_PATH + poster.getFilePath())
                .collect(Collectors.toSet());
        final Set<String> backdrops = images.getBackdrops().stream()
                .map(backdrop -> IMAGE_PATH + backdrop.getFilePath())
                .collect(Collectors.toSet());
        final Set<Photo> photos = new HashSet<>();
        posters.forEach(poster -> photos.add(photoRepository.findById(poster)
                .orElseGet(() -> {
                    Photo photo = new Photo();
                    photo.setPath(poster);
                    return photoRepository.save(photo);
                })));
        backdrops.forEach(backdrop ->
                photos.add(photoRepository.findById(backdrop).orElseGet(() -> {
                    Photo photo = new Photo();
                    photo.setPath(backdrop);
                    return photoRepository.save(photo);
                })));
        return photos;
    }

    private String getTrailer(VideoResults videos) {
        return videos.getResults().stream()
                .filter(video -> video.getType().equals(TRAILER))
                .map(trailer -> YOUTUBE_PATH + trailer.getKey())
                .findFirst().orElse(videos.getResults().stream()
                        .filter(video -> video.getType().equals(TEASER))
                        .map(teaser -> YOUTUBE_PATH + teaser.getKey())
                        .findFirst().orElse(null));
    }

    private Duration getDuration(Integer runtime) {
        if (durationRepository.findById(runtime).isPresent()) {
            return durationRepository.findById(runtime).get();
        }
        final Duration duration = new Duration();
        duration.setDuration(runtime);
        return durationRepository.save(duration);
    }

    private Set<Actor> getActors(List<Cast> casts) {
        return casts.stream().map(cast -> {
            final String name = cast.getName();
            if (actorRepository.findById(name).isPresent()) {
                return actorRepository.findById(name).get();
            }
            final Actor actor = new Actor();
            actor.setName(name);
            actor.setPhoto(IMAGE_PATH + cast.getProfilePath());
            return actorRepository.save(actor);
        }).collect(Collectors.toSet());
    }

    private Producer getProducer(List<Crew> crews) {
        return crews.stream().filter(crew -> {
            final String job = crew.getJob();
            if (job.equals(DIRECTOR)) {
                return true;
            }
            return job.equals(PRODUCER);
        })
                .map(producer -> {
                    final String name = producer.getName();
                    if (producerRepository.findById(name).isPresent()) {
                        return producerRepository.findById(name).get();
                    }
                    final Producer producerTmdb = new Producer();
                    producerTmdb.setName(name);
                    return producerRepository.save(producerTmdb);
                }).findFirst()
                .orElse(crews.stream().filter(crew -> crew.getJob().equals(PRODUCER))
                        .map(producer -> {
                            final String name = producer.getName();
                            if (producerRepository.findById(name).isPresent()) {
                                return producerRepository.findById(name).get();
                            }
                            final Producer producerTmdb = new Producer();
                            producerTmdb.setName(name);
                            return producerRepository.save(producerTmdb);
                        }).findFirst().orElse(null));
    }

    private Set<GenreEntity> getGenres(List<Genre> genres) {
        return genres.stream()
                .map(genre -> genreRepository.findById(genre.getId())
                        .orElseThrow(() -> new RuntimeException(
                                "Can't find genre by id " + genre.getId())))
                .collect(Collectors.toSet());
    }

    private Photo getPosterPath(String path) {
        if (photoRepository.findById(path).isPresent()) {
            return photoRepository.findById(path).get();
        }
        final Photo photo = new Photo();
        photo.setPath(path);
        return photoRepository.save(photo);
    }

    private Year getReleaseYear(String releaseDate) {
        Year year = new Year();
        year.setYear(Integer.valueOf(releaseDate.substring(0,4)));
        return yearRepository.save(year);
    }

    private Rating getRating(Double rating) {
        if (ratingRepository.findById(rating).isPresent()) {
            return ratingRepository.findById(rating).get();
        } else {
            final Rating ratingDb = new Rating();
            ratingDb.setRating(rating);
            return ratingRepository.save(ratingDb);
        }
    }

    private void loadGenres(String language) {
        final TmdbGenre genres;
        genres = tmdbApi.getGenre();
        try {
            if (genres != null && genres.getMovieList(language) != null) {
                try {
                    for (Genre genre : genres.getMovieList(language)) {
                        final GenreEntity genreEntity = new GenreEntity();
                        genreEntity.setId(genre.getId());
                        genreEntity.setName(genre.getName());
                        genreRepository.save(genreEntity);
                    }
                } catch (TmdbException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (TmdbException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteAll() {
        if (movieRepository != null) {
            movieRepository.deleteAll();
        }
        if (producerRepository != null) {
            producerRepository.deleteAll();
        }
        if (actorRepository != null) {
            actorRepository.deleteAll();
        }
        if (ratingRepository != null) {
            ratingRepository.deleteAll();
        }
        if (yearRepository != null) {
            yearRepository.deleteAll();
        }
        if (durationRepository != null) {
            durationRepository.deleteAll();
        }
        if (photoRepository != null) {
            photoRepository.deleteAll();
        }
        if (genreRepository != null) {
            genreRepository.deleteAll();
        }
    }
}
