package io.kodokojo.ha.service.marathon;

import com.google.gson.JsonObject;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.Path;

public interface MarathonRestApi {

    @Headers("Content-Type: application/json")
    @GET("/v2/apps/{appId}?embed=apps.tasks")
    JsonObject getAppdId(@Path("appId") String appId);
}
