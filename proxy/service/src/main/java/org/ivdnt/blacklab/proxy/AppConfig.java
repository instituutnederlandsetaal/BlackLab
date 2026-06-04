package org.ivdnt.blacklab.proxy;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.ivdnt.blacklab.proxy.backend.Backend;
import org.ivdnt.blacklab.proxy.backend.ProxyBackend;
import org.ivdnt.blacklab.proxy.resources.CorpusResource;
import org.ivdnt.blacklab.proxy.resources.RootResource;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

/**
 * Add any classes that Jersey needs to be aware of here (or add packages in which to use auto-discovering)
 */
public class AppConfig extends ResourceConfig {

	/**
	 * Creates a REST Client and closes it on dispose.
	 */
	private static class ClientFactory implements Factory<Client> {

		@Override
		public Client provide() {
//			ClientConfig configuration = new ClientConfig();
//			configuration = configuration.property(ClientProperties.CONNECT_TIMEOUT, 1000);
//			configuration = configuration.property(ClientProperties.READ_TIMEOUT, 1000);
//			return ClientBuilder.newClient(configuration);
			return ClientBuilder.newClient();
		}

		@Override
		public void dispose(Client service) {
			service.close();
		}
	}

    /** Creates a backend and closes it on dispose. */
    private static class BackendFactory implements Factory<Backend> {

        /** REST client to forward requests to the BlackLab instance we're proxying */
        Client client;

        @Inject
        public BackendFactory(Client client) {
            this.client = client;
        }

        @Override
        public Backend provide() {
            return new ProxyBackend(client); // new BlacklabBackend();
        }

        @Override
        public void dispose(Backend service) {
            service.close();
        }
    }

	public AppConfig() {
    	super(
			JacksonFeature.class, // Enable Jackson as our JAXB provider

			// Handle error responses by throwing a WebApplicationException
			ErrorResponseFilter.class,
			GenericExceptionMapper.class, // Map any exceptions thrown to an error response
			OutputTypeFilter.class, // "outputformat" parameter overrides Accept header
			CORSFilter.class, // add CORS headers to output

			// Our REST resources
			RootResource.class,
			CorpusResource.class
		);

		// Make sure the config file is read (or fail if not found)
		ProxyConfig.get();

		// Tell HK2 how to inject Client where needed
		register(new AbstractBinder() {
			@Override
			protected void configure() {
				// "when @Inject'ing a Client, use this factory and a singleton instance."
				// (Client is a heavy object and Jersey clients should be thread-safe)
                bindFactory(ClientFactory.class).to(Client.class).in(Singleton.class);
                bindFactory(BackendFactory.class).to(Backend.class).in(Singleton.class);
			}
		});
	}
}
