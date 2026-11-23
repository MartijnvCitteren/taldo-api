package eu.taldo.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The backend-for-frontend (ADR 0009): logs the browser in with Keycloak as a confidential client,
 * keeps the tokens in a server-side session and relays the frontend's API calls to taldo-api with
 * the user's access token. It holds no business logic and no data of its own.
 */
@SpringBootApplication
public class TaldoBffApplication {

  public static void main(String[] args) {
    SpringApplication.run(TaldoBffApplication.class, args);
  }
}
