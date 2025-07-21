package org.ivdnt.blacklab.proxy;

import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.ivdnt.blacklab.proxy.resources.CorpusResource;
import org.ivdnt.blacklab.proxy.resources.RootResource;

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
				// (Client is a heavy object and Jersey clients should be threadsafe)
				bindFactory(ClientFactory.class).to(Client.class).in(Singleton.class);
			}
		});
	}

}
