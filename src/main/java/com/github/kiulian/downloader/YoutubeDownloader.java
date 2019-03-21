package com.github.kiulian.downloader;

/*-
 * #
 * Java youtube video and audio downloader
 *
 * Copyright (C) 2019 Igor Kiulian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #
 */

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.kiulian.downloader.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class YoutubeDownloader {

    public interface DownloadCallback {

        void onDownloading(int progress);

        void onFinished(File file);

        void onError(Throwable throwable);
    }

    private static final String DETAILS = "\\\"videoDetails\\\":";
    private static final String ANNOTATIONS = ",\\\"annotations\\\"";
    private static final String FORMATS = "\\\"formats\\\"";

    public static YoutubeVideo getVideo(String videoId) throws YoutubeException, IOException {
        String page = loadPage("https://www.youtube.com/watch?v=" + videoId);

        VideoDetails videoDetails = new VideoDetails(videoId);

        int detailsIndex = page.indexOf(DETAILS);
        if (detailsIndex != -1) {
            int detailsIndexEnd = page.indexOf(ANNOTATIONS, detailsIndex);
            if (detailsIndexEnd != -1) {
                String details = page.substring(detailsIndex + DETAILS.length(), detailsIndexEnd)
                        .replaceAll("\\\\{1,2}\"", "\"");

                videoDetails.setDetails(JSON.parseObject(details));
            }
        }


        int beginIndex = page.indexOf(FORMATS);
        if (beginIndex == -1) {
            if (page.contains("\"status\":\"ERROR\""))
                throw new YoutubeException.VideoUnavailableException("Video unavailable");
            else
                throw new YoutubeException.BadPageException("Could not parse web page");
        }
        int endIndex = page.indexOf("}]}", beginIndex) + 3;
        String config = "{" + page.substring(beginIndex, endIndex)
                .replaceAll("\\\\{1,2}\"", "\"")
                .replace("\u0026", "&");

        JSONObject object;
        try {
            object = JSON.parseObject(config);
        } catch (Exception e) {
            throw new YoutubeException.BadPageException("Could not parse web page");
        }

        JSONArray jsonFormats = object.getJSONArray("formats");
        JSONArray jsonAdaptiveFormats = object.getJSONArray("adaptiveFormats");

        List<Format> formats = new ArrayList<>(jsonAdaptiveFormats.size() + jsonFormats.size());
        int i;
        for (i = 0; i < jsonFormats.size(); i++) {
            try {
                formats.add(new AudioVideoFormat(jsonFormats.getJSONObject(i)));
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        for (i = 0; i < jsonAdaptiveFormats.size(); i++) {
            try {
                JSONObject json = jsonAdaptiveFormats.getJSONObject(i);
                String mimeType = json.getString("mimeType");
                if (mimeType.contains(Constants.AUDIO))
                    formats.add(new AudioFormat(json));
                else if (mimeType.contains(Constants.VIDEO))
                    formats.add(new VideoFormat(json));
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        return new YoutubeVideo(videoDetails, formats);
    }

    private static String loadPage(String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                connection.getInputStream()));

        StringBuilder sb = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null)
            sb.append(inputLine);
        in.close();

        return sb.toString();
    }


}