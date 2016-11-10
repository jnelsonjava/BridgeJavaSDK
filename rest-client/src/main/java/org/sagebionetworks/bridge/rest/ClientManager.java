package org.sagebionetworks.bridge.rest;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.rest.model.ClientInfo;
import org.sagebionetworks.bridge.rest.model.Environment;
import org.sagebionetworks.bridge.rest.model.SignIn;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

/**
 * <p>ClientManager provides support for configuring API classes using values set via a properties 
 * file (~/bridge-sdk.properties), from environment variables, or from Java system variables. The 
 * key values you can set:</p>
 * 
 *  <dl>
 *      <dt>STUDY_IDENTIFIER</dt>
 *      <dd>The identifier of your study (study.identifier in properties file).</dd>
 *      <dt>ACCOUNT_EMAIL</dt>
 *      <dd>The email address of your account (account.email in properties file).</dd>
 *      <dt>ACCOUNT_PASSWORD</dt>
 *      <dd>The password of your account (account.password in properties file).</dd>
 *      <dt>LANGUAGES</dt>
 *      <dd>A comma-separated list of preferred languages for this client (most to least preferred). Optional 
 *          (languages in properties file).</dd>
 *  </dl>
 * 
 * <p>Once you provide credentials (through configuration or programmatically), clients retrieved 
 * through this class will re-authenticate the caller if the session expires.</p> 
 * 
 * <p>The ClientManager contacts our production servers unless you configure another 
 * <code>Environment</code>.</p>
 */
public final class ClientManager {

    public static interface ClientSupplier {
        ApiClientProvider get(String hostUrl, String userAgent, String acceptLanguage);
    }
    
    private static final Splitter SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    
    private static final Map<Environment,String> HOSTS = new ImmutableMap.Builder<Environment,String>()
            .put(Environment.LOCAL,"http://localhost:9000")
            .put(Environment.DEVELOP,"https://webservices-develop.sagebridge.org")
            .put(Environment.STAGING,"https://webservices-staging.sagebridge.org")
            .put(Environment.PRODUCTION,"https://webservices.sagebridge.org")
            .build();
    
    private static final ClientSupplier SUPPLIER = new ClientSupplier() {
        @Override public ApiClientProvider get(String hostUrl, String userAgent, String acceptLanguage) {
            return new ApiClientProvider(hostUrl, userAgent, acceptLanguage);
        }
    };
    
    private final Config config;
    private final ClientInfo clientInfo;
    private final transient SignIn signIn;
    private final List<String> acceptLanguages;
    private final ApiClientProvider apiClientProvider;
    
    private ClientManager(Config config, ClientInfo clientInfo, List<String> acceptLanguages, SignIn signIn,
            ClientSupplier provider) {
        checkNotNull(HOSTS.get(config.getEnvironment()));
        
        this.config = config;
        this.clientInfo = clientInfo;
        this.acceptLanguages = acceptLanguages;
        this.signIn = signIn;
        String hostUrl = HOSTS.get(config.getEnvironment());
        String userAgent = RestUtils.getUserAgent(clientInfo);
        String acceptLanguage = RestUtils.getAcceptLanguage(acceptLanguages);
        this.apiClientProvider = provider.get(hostUrl, userAgent, acceptLanguage);
    }

    public final Config getConfig() {
        return config;
    }
    
    public final ClientInfo getClientInfo() {
        return clientInfo;
    }
    
    public final List<String> getAcceptedLanguages() {
        return acceptLanguages;
    }
    
    public final String getHostUrl() {
        return HOSTS.get(config.getEnvironment());
    }

    /**
     * @param <T>
     *         One of the Api classes in the org.sagebionetworks.bridge.rest.api package.
     * @param service
     *         Class representing the service
     * @return service client
     */
    public final <T> T getClient(Class<T> service) {
        return apiClientProvider.getClient(service, signIn);
    }
    
    public final static class Builder {
        private Config config;
        private ClientInfo clientInfo;
        private List<String> acceptLanguages;
        private SignIn signIn;
        private ClientSupplier supplier;
        
        /**
         * Provide a factory for the production of ApiClientProvider (useful only for testing, a 
         * reasonable default factory is used without setting this).
         * @param supplier
         *      a factory to build ApiClientProvider
         * @return builder
         */
        public Builder withClientSupplier(ClientSupplier supplier) {
            this.supplier = supplier;
            return this;
        }
        
        /**
         * Provide a configuration object for this ClientManager. A default configuration
         * is created if none is provided.
         * @param config
         *      a Config object
         * @return builder
         */
        public Builder withConfig(Config config) {
            this.config = config;
            return this;
        }
        /**
         * Provide a ClientInfo object for requests made by clients from this ClientManager. 
         * This information is used to create a well-formed <code>User-Agent</code> header 
         * for requests.
         * @param clientInfo
         *      a ClientInfo object
         * @return builder
         */
        public Builder withClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            return this;
        }
        /**
         * Provide the languages this caller can accept to the server, in an ordered list of two-character 
         * language codes (in order of most preferred, to least preferred). Duplicate language codes are 
         * ignored (first in order takes preference). If the study is offered in more than one language, 
         * this preferred language may change the content returned from the server. These language 
         * preferences are considered optional by the Bridge server, though your specific study may require 
         * that a language be specified to deliver the correct content. If no language match occurs 
         * (or these preferences are not provided), virtually all studies are configured to return default 
         * content in the study's primary language.
         * @param acceptLanguages
         *      an optional, ordered list of two-letter language codes, from most preferred language to 
         *      least preferred language
         * @return builder
         */
        public Builder withAcceptLanguage(List<String> acceptLanguages) {
            this.acceptLanguages = acceptLanguages;
            return this;
        }
        /**
         * Provide the sign in credentials for clients from this ClientManager. This information 
         * can be provided via configuration, or programmatically through this object.
         * @param signIn
         *      a SignIn object
         * @return builder
         */
        public Builder withSignIn(SignIn signIn) {
            this.signIn = signIn;
            return this;
        }
        private ClientInfo getDefaultClientInfo() {
            ClientInfo info = new ClientInfo();
            info.setOsName(System.getProperty("os.name"));
            info.setOsVersion(System.getProperty("os.version"));
            info.setDeviceName(System.getProperty("os.arch"));
            info.setSdkName("BridgeJavaSDK");
            info.setSdkVersion(Integer.parseInt(config.getSdkVersion()));
            return info;
        }
        public ClientManager build() {
            if (this.signIn == null && this.config != null) {
                this.signIn = this.config.getAccountSignIn();
            }
            checkNotNull(signIn, "Sign in must be supplied to ClientManager builder.");
            checkNotNull(signIn.getStudy(), "Sign in must have a study identifier.");
            checkNotNull(signIn.getEmail(), "Sign in must specify an email address.");
            checkNotNull(signIn.getPassword(), "Sign in must specify a password.");
            if (this.config == null) {
                this.config = new Config();
            }
            if (this.config.getEnvironment() == null) {
                this.config.set(Environment.PRODUCTION);
            }
            if (this.acceptLanguages == null || this.acceptLanguages.isEmpty()) {
                String langs = this.config.getLanguages();
                this.acceptLanguages = SPLITTER.splitToList(langs);
            }
            ClientInfo info = getDefaultClientInfo();
            if (this.clientInfo != null) {
                if (clientInfo.getAppName() != null) {
                    info.setAppName(clientInfo.getAppName());
                }
                if (clientInfo.getAppVersion() != null) {
                    info.setAppVersion(clientInfo.getAppVersion());
                }
                if (clientInfo.getDeviceName() != null) {
                    info.setDeviceName(clientInfo.getDeviceName());
                }
                if (clientInfo.getOsName() != null) {
                    info.setOsName(clientInfo.getOsName());
                }
                if (clientInfo.getOsVersion() != null) {
                    info.setOsVersion(clientInfo.getOsVersion());
                }
            }
            if (this.supplier == null) {
                this.supplier = SUPPLIER;
            }
            return new ClientManager(config, info, acceptLanguages, signIn, supplier);
        }
    }
    
}
