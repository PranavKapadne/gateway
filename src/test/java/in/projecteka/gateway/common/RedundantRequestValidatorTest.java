package in.projecteka.gateway.common;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import in.projecteka.gateway.clients.ClientError;
import in.projecteka.gateway.common.cache.CacheAdapter;
import in.projecteka.gateway.common.cache.LoadingCacheAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static in.projecteka.gateway.common.TestBuilders.string;

class RedundantRequestValidatorTest {

    private static CacheAdapter<String, String> cacheForReplayAttack;

    @BeforeAll
    static void setUp() {
        cacheForReplayAttack = new LoadingCacheAdapter<String, String>(CacheBuilder
                .newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @SuppressWarnings("NullableProblems")
                    public String load(String key) {
                        return "";
                    }
                }));
    }

    private static Stream<Arguments> nonAllowedDates() {
        var pastDate = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2);
        var futureDate = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        return Stream.of(Arguments.of(pastDate), Arguments.of(futureDate));
    }

    @ParameterizedTest
    @MethodSource("nonAllowedDates")
    void shouldReturnFalseIfTimestampIsExpired(LocalDateTime time) {
        var validator = new RedundantRequestValidator(cacheForReplayAttack, "replay");

        StepVerifier
                .create(validator.validate(string(), time.toString()))
                .expectNext(false)
                .expectComplete()
                .verify();
    }

    @Test
    void shouldReturnFalseIfTimestampIsInNotValidFormat() {
        var validator = new RedundantRequestValidator(cacheForReplayAttack, "replay");

        StepVerifier
                .create(validator.validate(string(), string()))
                .expectNext(false)
                .expectComplete()
                .verify();
    }

    @Test
    void returnErrorIfEntryExists() {
        var requestValidator = new RedundantRequestValidator(cacheForReplayAttack, "replay");
        var requestId = string();
        var timestamp = string();
        requestValidator.put(requestId, timestamp).subscribe().dispose();

        StepVerifier
                .create(requestValidator.validate(requestId, timestamp))
                .expectErrorMatches(throwable -> throwable instanceof ClientError &&
                        ((ClientError) throwable).getHttpStatus().is4xxClientError())
                .verify();
    }

    @Test
    void returnTrueIfEntryDoesNotExists() {
        var requestValidator = new RedundantRequestValidator(cacheForReplayAttack, "replay");

        StepVerifier
                .create(requestValidator.validate(string(), LocalDateTime.now(ZoneOffset.UTC).toString()))
                .expectNext(true)
                .expectComplete()
                .verify();
    }
}
