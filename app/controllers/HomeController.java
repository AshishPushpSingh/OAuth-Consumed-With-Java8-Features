package controllers;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import play.libs.oauth.OAuth;
import play.libs.oauth.OAuth.RequestToken;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


public class HomeController extends Controller {

    /*
    Consumer-Key and Consumer-Secret is obtained from the app which we have created in QuickBooks.
    For more information and exploration you can go to "https://developer.intuit.com/"
    Use your own Keys. ;)
    */
    static final OAuth.ConsumerKey KEY = new OAuth.ConsumerKey(
            "qyprdvsO30Bw5B9uy1F9vahxxxxxxx",
            "QwRs4IIHsm1W5TlWL0w7HD50QLLRBFpisxxxxxxx");
    /*
    The Request token URL, Access token URL and Authorization URL for QuickBooks Online are available at
    "https://developer.intuit.com/docs/0100_quickbooks_online/0100_essentials/000500_authentication_and_authorization
    /connect_from_within_your_app"
    */
    private static final OAuth.ServiceInfo SERVICE_INFO =
            new OAuth.ServiceInfo("https://oauth.intuit.com/oauth/v1/get_request_token",
                    "https://oauth.intuit.com/oauth/v1/get_access_token",
                    "https://appcenter.intuit.com/Connect/Begin",
                    KEY);

    //The Oauth variable for making oauth 1.0 calls
    private static final OAuth QB = new OAuth(SERVICE_INFO);
    private String token;
    private String secret;
    private String realmId;
    private WSClient ws;

    //Using google-guice for Injecting WSClient
    @Inject
    public HomeController(WSClient ws) {
        this.ws = ws;
    }

    // Returning a Future promise of Result instead of directly returning result. This gives your application
    // concurrency and better performance.
    public CompletionStage<Result> getDataFromQB() {

        Optional<RequestToken> sessionTokenPair = getSessionTokenPair();
        //The Url returns the list of invoices from QuickBooks.
        String urltoAPI =
                "https://sandbox-quickbooks.api.intuit.com/v3/company/" +
                        realmId +
                        "/query?query=select%20%2A%20from%20invoice&minorversion=4";

        if (sessionTokenPair.isPresent()) {

            CompletionStage<WSResponse> futureResponse = ws.url(urltoAPI).setMethod("GET")
                    .setHeader("accept", "application/json")
                    .sign(new OAuth.OAuthCalculator(HomeController.KEY, sessionTokenPair.get()))
                    .get();

            //As futureResponse here is a Future Implementation, the program doesn't get blocked.
            return futureResponse.thenApplyAsync((WSResponse response) -> {
                int status = response.getStatus();
                // Check that the response was successful
                if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_CREATED) {
                    String body = response.getBody();
                    // Get the content type
                    return ok(
                            body +
                                    "\n\n token = " + token +
                                    "\n secret  = " + secret +
                                    "\n companyId = " + realmId)
                            .as("application/json");
                } else {
                    return redirect(routes.HomeController.auth());
                }
            });
        }
        return CompletableFuture.completedFuture(redirect(routes.HomeController.auth()));
    }

    public Result auth() {

        String verifier = request().getQueryString("oauth_verifier");
        realmId = request().getQueryString("realmId");
        if (Strings.isNullOrEmpty(verifier)) {
            String url = routes.HomeController.auth().absoluteURL(request());
            RequestToken requestToken = QB.retrieveRequestToken(url);
            saveTokenPair(requestToken);
            return redirect(QB.redirectUrl(requestToken.token));
        } else {
            RequestToken requestToken = getSessionTokenPair().get();
            RequestToken accessToken = QB.retrieveAccessToken(requestToken, verifier);
            saveTokenPair(accessToken);
            return redirect(routes.HomeController.getDataFromQB());
        }
    }

    private void saveTokenPair(RequestToken requestToken) {
        session("token", requestToken.token);
        session("secret", requestToken.secret);
        this.token = requestToken.token;
        this.secret = requestToken.secret;

    }

    private Optional<RequestToken> getSessionTokenPair() {
        if (session().containsKey("token")) {
            return Optional.ofNullable(new RequestToken(session("token"), session("secret")));
        }
        return Optional.empty();
    }
}