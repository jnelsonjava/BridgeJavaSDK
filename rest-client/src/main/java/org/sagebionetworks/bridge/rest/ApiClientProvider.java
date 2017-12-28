package org.sagebionetworks.bridge.rest;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.model.ClientInfo;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

/**
 * Base class for creating clients that are correctly configured to communicate with the
 * Bridge server.
 */
public class ApiClientProvider {

    private static final Interceptor WARNING_INTERCEPTOR = new WarningHeaderInterceptor();
    private static final Interceptor ERROR_INTERCEPTOR = new ErrorResponseInterceptor();
    private static final Interceptor LOGGING_INTERCEPTOR = new LoggingInterceptor();

    private final UserSessionInfoProvider userSessionInfoProvider;
    private final LoadingCache<Class<?>, ?> unauthenticatedServices;
    private final LoadingCache<Class<?>, ?> authenticatedServices;

    private ApiClientProvider(final UserSessionInfoProvider userSessionInfoProvider,
            final Retrofit unauthenticatedRetrofit, final Retrofit authenticatedRetrofit) {
        this.userSessionInfoProvider = userSessionInfoProvider;

        this.unauthenticatedServices = CacheBuilder.newBuilder()
                .build(new CacheLoader<Class<?>, Object>() {
                    @Override public Object load(Class<?> serviceClass) {
                        return unauthenticatedRetrofit.create(serviceClass);
                    }
                });

        this.authenticatedServices = CacheBuilder.newBuilder()
                .build(new CacheLoader<Class<?>, Object>() {
                    @Override public Object load(Class<?> serviceClass) {
                        return authenticatedRetrofit.create(serviceClass);
                    }
                });
    }

    // To build the ClientManager on this class, we need to have access to the session that is persisted
    // by the HTTP interceptors.
    public UserSessionInfoProvider getUserSessionInfoProvider() {
        return userSessionInfoProvider;
    }

    /**
     * Create an unauthenticated client (this client cannot authenticate automatically, and is only used for
     * public APIs not requiring a server user to access).
     *
     * @param <T>
     *         One of the Api classes in the org.sagebionetworks.bridge.rest.api package.
     * @param service
     *         Class representing the service
     * @return service client
     */
    public <T> T getClient(Class<T> service) {
        checkNotNull(service);

        //noinspection unchecked
        return (T) unauthenticatedServices.getUnchecked(service);
    }

    /**
     * @param <T>
     *         One of the Api classes in the org.sagebionetworks.bridge.rest.api package.
     * @param service
     *         Class representing the service
     * @param signIn
     *         credentials for the user, or null for an unauthenticated client
     * @return service client that is authenticated with the user's credentials
     */
    public <T> T getClient(Class<T> service, SignIn signIn) {
        checkNotNull(service);
        checkNotNull(signIn);

        //noinspection unchecked
        return (T) authenticatedServices.getUnchecked(service);
    }

    public static class Builder {
        private String baseUrl;
        private String userAgent;
        private String acceptLanguage;
        private String study;
        private String email;

        private String password;
        private UserSessionInfo session;

        /**
         * Creates a builder for accessing services associated with an environment, study, and participant.
         *
         * @param baseUrl base url for Bridge service
         * @param userAgent
         *         user-agent string in Bridge's expected format, see {@link RestUtils#getUserAgent(ClientInfo)}
         * @param acceptLanguage
         *         optional comma-separated list of preferred languages for this client (most to least
         *         preferred
         * @param study
         *         study identifier
         * @param email
         *         email of participant
         */
        public Builder(String baseUrl, String userAgent, String acceptLanguage, String study, String email) {
            checkState(!Strings.isNullOrEmpty(baseUrl));
            checkState(!Strings.isNullOrEmpty(userAgent));
            checkState(!Strings.isNullOrEmpty(study));
            checkState(!Strings.isNullOrEmpty(email));

            this.baseUrl = baseUrl;
            this.userAgent = userAgent;
            this.acceptLanguage = acceptLanguage;
            this.study = study;
            this.email = email;
        }

        /**
         * @param password participant's password, if available
         * @return this builder, for chaining operations
         */
        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param session participant's last active session, if available
         * @return this builder, for chaining operations
         */
        public Builder withSession(UserSessionInfo session) {
            this.session = session;
            return this;
        }

        public ApiClientProvider build() {
            checkState(!Strings.isNullOrEmpty(password) || session != null,
                    "requires at least one of password or session");

            Retrofit unauthenticatedRetrofit = getRetrofit(getHttpClientBuilder().build());

            AuthenticationApi authenticationApi = unauthenticatedRetrofit.create(AuthenticationApi.class);
            UserSessionInfoProvider sessionProvider =
                    new UserSessionInfoProvider(authenticationApi, study, email, password, session);

            UserSessionInterceptor sessionInterceptor = new UserSessionInterceptor(sessionProvider);
            AuthenticationHandler authenticationHandler = new AuthenticationHandler(sessionProvider);

            OkHttpClient.Builder httpClientBuilder = getHttpClientBuilder(sessionInterceptor, authenticationHandler);
            httpClientBuilder.authenticator(authenticationHandler);

            Retrofit authenticatedRetrofit = getRetrofit(httpClientBuilder.build());

            return new ApiClientProvider(sessionProvider, unauthenticatedRetrofit, authenticatedRetrofit);
        }

        OkHttpClient.Builder getHttpClientBuilder(Interceptor... interceptors) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(2, TimeUnit.MINUTES)
                    .writeTimeout(2, TimeUnit.MINUTES);
            for (Interceptor interceptor : interceptors) {
                builder.addInterceptor(interceptor);
            }
            return builder
                    .addInterceptor(new HeaderInterceptor(userAgent, acceptLanguage))
                    .addInterceptor(WARNING_INTERCEPTOR)
                    .addInterceptor(ERROR_INTERCEPTOR)
                    .addInterceptor(LOGGING_INTERCEPTOR);
        }

        Retrofit getRetrofit(OkHttpClient client) {
            return new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(RestUtils.GSON))
                    .build();
        }
    }
}
