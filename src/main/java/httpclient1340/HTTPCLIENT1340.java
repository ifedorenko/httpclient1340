package httpclient1340;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;

public class HTTPCLIENT1340
{
    private Server server;

    public static void main( String[] args )
        throws Exception
    {
        // enable httpclient write+
        // -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog
        // -Dorg.apache.commons.logging.simplelog.showdatetime=true
        // -Dorg.apache.commons.logging.simplelog.log.org.apache.http=DEBUG
        System.setProperty( "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog" );
        System.setProperty( "org.apache.commons.logging.simplelog.showdatetime", "true" );
        System.setProperty( "org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG" );

        new HTTPCLIENT1340().run();
    }

    public void run()
        throws Exception
    {
        String username = "username";
        String password = "password";

        String baseUrl = startServer( 8888, new File( "src/main/resources" ), username, password );

        DefaultHttpClient httpclient = new DefaultHttpClient();

        List<String> authorisationPreference = new ArrayList<String>();
        authorisationPreference.add( AuthPolicy.DIGEST );
        authorisationPreference.add( AuthPolicy.BASIC );
        Credentials credentials = null;
        credentials = new UsernamePasswordCredentials( username, password );
        httpclient.getCredentialsProvider().setCredentials( AuthScope.ANY, credentials );
        httpclient.getParams().setParameter( AuthPNames.TARGET_AUTH_PREF, authorisationPreference );

        try
        {
            executeRequest( httpclient, baseUrl );
            executeRequest( httpclient, baseUrl );
        }
        finally
        {
            server.stop();
        }
    }

    private void executeRequest( DefaultHttpClient httpclient, String baseUrl )
        throws IOException, ClientProtocolException
    {
        HttpGet httpGet = new HttpGet( baseUrl + "/file.txt" );
        HttpResponse response = httpclient.execute( httpGet );
        EntityUtils.consume( response.getEntity() );
        if ( response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() > 299 )
        {
            throw new IOException( response.getStatusLine().getReasonPhrase() );
        }
    }

    /**
     * Starts test (jetty) server on specified port. The server will serve files from specified basedir and will
     * require specified username/password.
     * <p>
     * The server sets Connection:Keep-Alive http response header for unauthorized requests (default jetty behaviour).
     * <p>
     * The server sets Connection:close http response header for successful requests (see anonymous ServletHolder#handle
     * implemented in this method)
     */
    private String startServer( int port, File basedir, String username, String password )
        throws Exception
    {
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        server = new Server();

        server.setHandler( contexts );

        Connector connector = new SocketConnector();
        connector.setPort( port );
        connector.setMaxIdleTime( 5000 );
        server.addConnector( connector );

        if ( username != null )
        {
            HashLoginService userRealm = new HashLoginService( "default" );
            userRealm.putUser( username, new Password( password ), new String[] { Constraint.ANY_ROLE } );

            Constraint constraint = new Constraint( "default", Constraint.ANY_ROLE );
            constraint.setAuthenticate( true );
            ConstraintMapping constraintMapping = new ConstraintMapping();
            constraintMapping.setPathSpec( "/*" );
            constraintMapping.setConstraint( constraint );

            ConstraintSecurityHandler securedHandler = new ConstraintSecurityHandler();
            securedHandler.setAuthenticator( new BasicAuthenticator() );
            securedHandler.addConstraintMapping( constraintMapping );
            securedHandler.setLoginService( userRealm );

            // chain handlers together
            securedHandler.setHandler( contexts );
            server.setHandler( securedHandler );
        }

        server.start();

        String contextName = "httpclient1340";
        ServletContextHandler context = new ServletContextHandler( contexts, URIUtil.SLASH + contextName );
        context.setResourceBase( basedir.getAbsolutePath() );
        DefaultServlet servlet = new DefaultServlet();
        ServletHolder holder = new ServletHolder( servlet )
        {
            @Override
            public void handle( Request baseRequest, ServletRequest request, ServletResponse response )
                throws ServletException, UnavailableException, IOException
            {
                ( (HttpServletResponse) response ).addHeader( "Connection", "close" );
                super.handle( baseRequest, request, response );
            }
        };
        context.addServlet( holder, URIUtil.SLASH );
        contexts.addHandler( context );
        try
        {
            context.start();
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e );
        }

        return "http://localhost:" + port + "/" + contextName;

    }

}
