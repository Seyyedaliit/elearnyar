package ir.ac.uk.elearn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class NetworkHelper {

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) return false;
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                return netInfo != null && netInfo.isConnected();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isNetworkException(Throwable t) {
        if (t == null) return false;
        if (t instanceof UnknownHostException ||
            t instanceof SocketTimeoutException ||
            t instanceof ConnectException ||
            t instanceof NoRouteToHostException) {
            return true;
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            if (isNetworkException(cause)) return true;
        }
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            return lower.contains("unable to resolve host") ||
                   lower.contains("failed to connect") ||
                   lower.contains("network is unreachable") ||
                   lower.contains("timed out") ||
                   lower.contains("connection refused");
        }
        return false;
    }
}
