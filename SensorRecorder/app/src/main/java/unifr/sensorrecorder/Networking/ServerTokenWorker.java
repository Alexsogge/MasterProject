package unifr.sensorrecorder.Networking;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import unifr.sensorrecorder.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;

import okhttp3.Request;
import okhttp3.Response;

public class ServerTokenWorker extends NetworkWorker{


    public ServerTokenWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        sendStatus(STATUS_PENDING);
        if(configs.getString(context.getString(R.string.conf_serverName), "").equals("")){
            return Result.failure(new Data.Builder().putInt(STATUS, STATUS_ERROR).build());
        }
        if(!configs.getString(context.getString(R.string.conf_serverToken), "").equals("")) {
            return Result.success(new Data.Builder().putInt(STATUS, STATUS_FINISHED).build());
        }
        int requestStatus = getServerToken();
        if(requestStatus != STATUS_SUCCESS){
            sendStatus(requestStatus);
            return Result.retry();
        }
        return Result.success(new Data.Builder().putInt(STATUS, STATUS_FINISHED).build());
    }


    private int getServerToken(){
        String serverName = configs.getString(context.getString(R.string.conf_serverName), "");
        String userIdentifier = configs.getString(context.getString(R.string.conf_userIdentifier), "");
        Request request = new Request.Builder()
                .url(serverName + "/tokenauth/request/?identifier="+userIdentifier)
                .build();
        Log.d("http", "request token from " + serverName + "/tokenauth/request/?identifier="+userIdentifier);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()){
                return STATUS_ERROR;
            } else {
                String jsonData = response.body().string();
                JSONObject jObject = new JSONObject(jsonData);
                String status = jObject.getString("status");
                if (status.equals("grant")){
                    SharedPreferences.Editor configEditor = configs.edit();
                    configEditor.putString(context.getString(R.string.conf_serverToken), jObject.getString("token"));
                    configEditor.apply();
                    makeToast(context.getString(R.string.toast_auth_granted));
                    return STATUS_SUCCESS;
                } else{
                    makeToast(context.getString(R.string.toast_not_auth) + jObject.getString("msg"));
                    return STATUS_PENDING;
                }
            }

            //System.out.println(response.body().string());
        } catch (ConnectException e){
            makeToast(context.getString(R.string.toast_cant_conn_to_server));
            return STATUS_ERROR;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            makeToast(context.getString(R.string.toast_cant_conn_to_server));
            return STATUS_ERROR;
        }
    }
}
