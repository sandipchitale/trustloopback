package sandipchitale.trustloopback;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;

@SpringBootApplication
public class TrustloopbackApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrustloopbackApplication.class, args);
	}

	@RestController
	public static class IndexController {

		@GetMapping("/")
		public String index() {
			return "Hello World";
		}
	}

	private static class LoopbackIPHostnameVerifier implements HostnameVerifier {
		@Override
		public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
			return "127.0.0.1".equals(hostname);
		}
	}

	@Bean
	public CommandLineRunner clr (RestTemplateBuilder restTemplateBuilder, SslBundles sslBundles) {
		return (String... args) -> {
			SslBundle clientSslBundle = sslBundles.getBundle("client");
			RestTemplate restTemplate;
			restTemplate = restTemplateBuilder.setSslBundle(clientSslBundle).build();
			System.out.println(restTemplate.getForObject("https://localhost:8080/", String.class));
			System.out.println(restTemplate.getForObject("https://server1:8080/", String.class));

			try {
                System.out.println("Trying to access https://127.0.0.1:8080/ with SslBundle 'client' expecting failure");
				System.out.println(restTemplate.getForObject("https://127.0.0.1:8080/", String.class));
			} catch (RestClientException restClientException) {
				System.out.println("Expected exception: " + restClientException.getMessage());
			}

			restTemplate = restTemplateBuilder.requestFactory(() -> {
                // Build request factory with SSLContext from SslBundle but with a custom LoopbackIPHostnameVerifier
                SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                        clientSslBundle
                                .getManagers()
                                .createSslContext(clientSslBundle.getProtocol()),
                        new LoopbackIPHostnameVerifier());

                HttpClientConnectionManager httpClientConnectionManager = PoolingHttpClientConnectionManagerBuilder
                        .create()
                        .setSSLSocketFactory(sslConnectionSocketFactory)
                        .build();
                CloseableHttpClient closeableHttpClient = HttpClients.custom()
                                                                     .setConnectionManager(httpClientConnectionManager)
                                                                     .evictExpiredConnections()
                                                                     .build();
                HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(closeableHttpClient);
				return requestFactory;
			}).build();

            try {
                System.out.println("Trying to access https://127.0.0.1:8080/ with SslBundle 'client' but with a custom LoopbackIPHostnameVerifier expecting success");
                System.out.println(restTemplate.getForObject("https://127.0.0.1:8080/", String.class));
				System.out.println("Success");
            } catch (RestClientException e) {
                System.out.println("Unexpected Exception:" + e.getMessage());
            }
		};
	}
}
