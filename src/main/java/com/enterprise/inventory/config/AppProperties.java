package com.enterprise.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Api api = new Api();
    private Security security = new Security();
    private Jobs jobs = new Jobs();

    @Getter
    @Setter
    public static class Api {
        private String basePath = "/api/v1";
        private Auth auth = new Auth();
        private Inventory inventory = new Inventory();
        private Users users = new Users();

        @Getter
        @Setter
        public static class Auth {
            private String base = "/auth";
            private String login = "/login";
            private String refresh = "/refresh";
            private String logout = "/logout";
        }

        @Getter
        @Setter
        public static class Inventory {
            private String base = "/inventory";
            private String receive = "/receive";
            private String receiveBatch = "/receive/batch";
            private String pickReserve = "/pick/reserve";
            private String pickConfirm = "/pick/confirm";
            private String pickRelease = "/pick/release";
            private String tasksGenerate = "/tasks/generate";
            private String movementsMe = "/movements/me";
        }

        @Getter
        @Setter
        public static class Users {
            private String base = "/users";
            private String batchPermissions = "/batch/permissions";
        }
    }

    @Getter
    @Setter
    public static class Security {
        private RateLimit rateLimit = new RateLimit();
        private TokenBlacklist blacklist = new TokenBlacklist();
        private Locks locks = new Locks();
        private Password password = new Password();

        @Getter
        @Setter
        public static class RateLimit {
            private List<String> authEndpoints = List.of("/api/v1/auth/login", "/api/v1/auth/refresh");
            private BucketConfig userBucket = new BucketConfig(60, 60, Duration.ofMinutes(1), 20, Duration.ofMinutes(10));
            private BucketConfig ipBucket = new BucketConfig(10, 10, Duration.ofMinutes(1), 10, Duration.ofMinutes(10));

            @Getter
            @Setter
            public static class BucketConfig {
                private long capacity;
                private long refillTokens;
                private Duration refillDuration;
                private long burstCapacity;
                private Duration cacheDuration;

                public BucketConfig() {}

                public BucketConfig(long capacity, long refillTokens, Duration refillDuration, long burstCapacity, Duration cacheDuration) {
                    this.capacity = capacity;
                    this.refillTokens = refillTokens;
                    this.refillDuration = refillDuration;
                    this.burstCapacity = burstCapacity;
                    this.cacheDuration = cacheDuration;
                }
            }
        }

        @Getter
        @Setter
        public static class TokenBlacklist {
            private Duration cacheDuration = Duration.ofHours(2);
        }

        @Getter
        @Setter
        public static class Locks {
            private Duration reservationTtl = Duration.ofHours(2);
            private Duration idempotencyTtl = Duration.ofHours(24);
        }

        @Getter
        @Setter
        public static class Password {
            private int bcryptStrength = 12;
        }
    }

    @Getter
    @Setter
    public static class Jobs {
        private Sweeper sweeper = new Sweeper();

        @Getter
        @Setter
        public static class Sweeper {
            private String cron = "0 0 * * * *";
        }
    }
}
