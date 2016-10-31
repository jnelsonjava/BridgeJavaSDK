package org.sagebionetworks.bridge.sdk.rest;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import org.sagebionetworks.bridge.sdk.rest.model.SignIn;

/**
 * Created by liujoshua on 10/11/16.
 */
public class ApiClientProvider {
    
    private final OkHttpClient unauthenticatedOkHttpClient;
    private final Retrofit.Builder retrofitBuilder;
    private final UserSessionInfoProvider userSessionInfoProvider;
    private final Map<SignIn, WeakReference<Retrofit>> authenticatedRetrofits;
    private final Map<SignIn, Map<Class<?>, WeakReference<?>>> authenticatedClients;

    public ApiClientProvider(String baseUrl, String userAgent) {
        this(baseUrl, userAgent, null);
    }

    // allow unit tests to inject a UserSessionInfoProvider
    ApiClientProvider(String baseUrl, String userAgent, UserSessionInfoProvider userSessionInfoProvider) {
        authenticatedRetrofits = Maps.newHashMap();
        authenticatedClients = Maps.newHashMap();
        
        UserSessionInterceptor sessionInterceptor = new UserSessionInterceptor();

        // 5 second timeout: creating studies takes awhile, and tests fail if the timeout is shorter.
        // May want to make it possible to configure this value when creating the ApiClientProvider.
        unauthenticatedOkHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // server times out after 30 seconds, past this it's pointless to wait.
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(sessionInterceptor)
                .addInterceptor(new HeaderInterceptor(userAgent))
                .addInterceptor(new DeprecationInterceptor())
                .addInterceptor(new ErrorResponseInterceptor())
                .addInterceptor(new LoggingInterceptor()).build();

        retrofitBuilder = new Retrofit.Builder().baseUrl(baseUrl)
                .client(unauthenticatedOkHttpClient)
                .addConverterFactory(GsonConverterFactory.create(RestUtils.GSON));
        
        this.userSessionInfoProvider = userSessionInfoProvider != null ? userSessionInfoProvider
                : new UserSessionInfoProvider(getAuthenticatedRetrofit(null), sessionInterceptor);
    }

    /**
     * Creates an unauthenticated client.
     *
     * @param service
     *         Class representing the service
     * @return service client
     */
    public <T> T getClient(Class<T> service) {
        return getClientImpl(service, null);
    }
    
    /**
     * @param service
     *         Class representing the service
     * @param signIn
     *         credentials for the user, or null for an unauthenticated client
     * @return service client that is authenticated with the user's credentials
     */
    public <T> T getClient(Class<T> service, SignIn signIn) {
        Preconditions.checkNotNull(signIn);

        return getClientImpl(service, signIn);
    }

    @SuppressWarnings("unchecked")
    private <T> T getClientImpl(Class<T> service, SignIn signIn) {
        Map<Class<?>, WeakReference<?>> userClients = authenticatedClients.get(signIn);
        if (userClients == null) {
            userClients = Maps.newHashMap();
            authenticatedClients.put(signIn, userClients);
        }

        T authenticateClient = null;
        WeakReference<?> clientReference = userClients.get(service);

        if (clientReference != null) {
            authenticateClient = (T) clientReference.get();
        }

        if (authenticateClient == null) {
            authenticateClient = getAuthenticatedRetrofit(signIn).create(service);
            userClients.put(service, new WeakReference<>(authenticateClient));
        }

        return authenticateClient;
    }

    Retrofit getAuthenticatedRetrofit(SignIn signIn) {
        Retrofit authenticatedRetrofit = null;

        WeakReference<Retrofit> authenticatedRetrofitReference = authenticatedRetrofits.get(signIn);
        if (authenticatedRetrofitReference != null) {
            authenticatedRetrofit = authenticatedRetrofitReference.get();
        }

        if (authenticatedRetrofit == null) {
            authenticatedRetrofit = createAuthenticatedRetrofit(signIn, null);
        }

        return authenticatedRetrofit;
    }

    // allow test to inject retrofit
    Retrofit createAuthenticatedRetrofit(SignIn signIn, AuthenticationHandler handler) {
        OkHttpClient.Builder httpClientBuilder = unauthenticatedOkHttpClient.newBuilder();

        if (signIn != null) {
            AuthenticationHandler authenticationHandler = handler;
            // this is the normal code path (only tests will inject a handler)
            if (authenticationHandler == null) {
                authenticationHandler = new AuthenticationHandler(signIn,
                        userSessionInfoProvider
                );
            }
            httpClientBuilder.addInterceptor(authenticationHandler).authenticator(authenticationHandler);
        }

        Retrofit authenticatedRetrofit = retrofitBuilder.client(httpClientBuilder.build()).build();
        authenticatedRetrofits.put(signIn,
                new WeakReference<>(authenticatedRetrofit));
        return authenticatedRetrofit;
    }

    // allow test access to retrofit builder
    Retrofit.Builder getRetrofitBuilder() {
        return retrofitBuilder;
    }
}
