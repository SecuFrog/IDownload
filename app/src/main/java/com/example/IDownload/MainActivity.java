package com.example.IDownload;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestVideoFileDownload;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.VideoWithAudioFormat;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private VideoInfo searchedVideo;
    private EditText urlEdit;
    private Button searchButton;
    private YoutubeDownloader downloader;
    private RelativeLayout resultLayout;
    private ImageView thumbnailImageView;
    private TextView videoTitleTextView;
    private Button downloadVideo;
    private Button downloadAudio;
    private ProgressBar progressBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlEdit = findViewById(R.id.urlEditText);
        searchButton = findViewById(R.id.searchButton);
        resultLayout = findViewById(R.id.resultLayout);
        thumbnailImageView = findViewById(R.id.thumbnailImageView);
        videoTitleTextView = findViewById(R.id.videoTitleTextView);
        downloadVideo = findViewById(R.id.downloadVideoButton);
        downloadAudio = findViewById(R.id.downloadAudioButton);
        progressBar = findViewById(R.id.progressWheel);

        // Initialize YoutubeDownloader
        downloader = new YoutubeDownloader();
    }

    public void onSearchButtonClick(View view) {
        String videoUrl = urlEdit.getText().toString().trim();
        String videoId = extractVideoId(videoUrl);
        searchVideo(videoId);
    }

    private String extractVideoId(String videoUrl) {
        // Use a regular expression to extract the video ID from the URL
        String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed\\\r\n|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(videoUrl);

        if (matcher.find()) {
            return matcher.group();
        } else {
            return null;
        }
    }

    private void searchVideo(String videoId) {
        new AsyncTask<String, Void, VideoInfo>() {
            @Override
            protected VideoInfo doInBackground(String... params) {
                String videoId = params[0];

                try {
                    Response<VideoInfo> response = downloader.getVideoInfo(new RequestVideoInfo(videoId));
                    return response.data();
                } catch (Exception e) {
                    Log.e("SearchVideo", "Error searching video", e);
                }

                return null;
            }

            @Override
            protected void onPostExecute(VideoInfo video) {
                if (video != null) {
                    searchedVideo = video;
                    displayVideoDetails(video);
                } else {
                    Toast.makeText(MainActivity.this, "Error searching video", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(videoId);
    }

    private void displayVideoDetails(VideoInfo video) {
        VideoDetails details = video.details();

        if (details != null) {
            resultLayout.setVisibility(View.VISIBLE);

            // Display video thumbnail
            Picasso.get().load(details.thumbnails().get(0)).into(thumbnailImageView);

            // Display video title
            videoTitleTextView.setText(details.title());
        } else {
            Toast.makeText(MainActivity.this, "Error retrieving video details", Toast.LENGTH_SHORT).show();
        }
    }

    public void onDownloadVideoClick(View view) {
        downloadVideo();
    }

    public void onDownloadAudioClick(View view) {
        downloadAudio();
    }

    private void downloadVideo() {
        if (searchedVideo != null) {
            // Get a list of available video formats
            List<VideoWithAudioFormat> videoFormats = searchedVideo.videoWithAudioFormats();

            // Prepare a CharSequence array for the dialog
            CharSequence[] formatNames = new CharSequence[videoFormats.size() + 1];
            formatNames[0] = "Best Video Format"; // Add an option for the best video format
            for (int i = 0; i < videoFormats.size(); i++) {
                String quality = videoFormats.get(i).qualityLabel();
                String extension = videoFormats.get(i).mimeType().split(";")[0].trim();
                formatNames[i + 1] = quality + " (" + extension + ")";
            }

            // Create a dialog to let the user choose the video format
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Choose Video Format")
                    .setItems(formatNames, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // User selected a format, proceed with the download
                            if (which == 0) {
                                // Download the best video format
                                downloadSelectedVideo((VideoWithAudioFormat) searchedVideo.bestVideoWithAudioFormat());
                            } else {
                                // Download the selected video format
                                downloadSelectedVideo(videoFormats.get(which - 1));
                            }
                        }
                    });
            builder.show();
        } else {
            Toast.makeText(MainActivity.this, "Error retrieving video formats", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadSelectedVideo(VideoWithAudioFormat selectedFormat) {
        new AsyncTask<Void, Void, File>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                // Show the progress wheel before starting the download
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected File doInBackground(Void... voids) {
                try {
                    if (searchedVideo != null) {
                        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "IDownloads");
                        RequestVideoFileDownload request = new RequestVideoFileDownload(selectedFormat)
                                .saveTo(outputDir);
                        Response<File> downloadResponse = downloader.downloadVideoFile(request);
                        Log.d("DownloadResponseVideo", "Result: " + downloadResponse.data());
                        Log.d("SelectedVideoFormat", "Result: " + selectedFormat.qualityLabel());
                        return downloadResponse.data();
                    }
                } catch (Exception e) {
                    Log.e("DownloadVideo", "Error downloading video", e);
                }

                return null;
            }

            @Override
            protected void onPostExecute(File result) {
                progressBar.setVisibility(View.GONE);
                Log.d("DownloadVideo", "Result: " + result);
                if (result != null) {
                    // Rename the downloaded file with the desired filename
                    File renamedFile = new File(result.getParent(), searchedVideo.details().title());
                    if (result.renameTo(renamedFile)) {
                        // Insert the renamed file into the media store
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, searchedVideo.details().title());
                        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, selectedFormat.mimeType().split(";")[0].trim());

                        Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;  // Use Video.Media for video files
                        Uri itemUri = getContentResolver().insert(contentUri, contentValues);

                        try (OutputStream outputStream = getContentResolver().openOutputStream(itemUri)) {
                            if (outputStream != null) {
                                FileInputStream inputStream = new FileInputStream(renamedFile);

                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }

                                inputStream.close();
                                outputStream.close();
                            }
                        } catch (IOException e) {
                            Log.e("DownloadVideo", "Error adding to media store", e);
                        }

                        Toast.makeText(MainActivity.this, "Video downloaded successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("DownloadVideo", "Error renaming file");
                        Toast.makeText(MainActivity.this, "Error renaming file", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Error downloading Video", Toast.LENGTH_SHORT).show();
                }
            }

        }.execute();
    }

    private void downloadAudio() {
        if (searchedVideo != null) {
            // Get a list of available audio formats
            List<AudioFormat> audioFormats = searchedVideo.audioFormats();

            // Prepare a CharSequence array for the dialog
            List<CharSequence> formatNamesList = new ArrayList<>();
            formatNamesList.add("Best Audio Format");  // Set the first element outside the loop

            for (AudioFormat format : audioFormats) {
                String quality = String.valueOf(format.audioQuality());
                String extension = format.mimeType().split(";")[0].trim();

                // Exclude audio/webm formats
                if (!extension.equalsIgnoreCase("audio/webm")) {
                    formatNamesList.add(quality + " (" + extension + ")");
                }
            }

            // Convert the List to an array
            CharSequence[] formatNames = formatNamesList.toArray(new CharSequence[0]);

            // Create a dialog to let the user choose the audio format
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Choose Audio Format")
                    .setItems(formatNames, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // User selected a format, proceed with the download
                            if (which == 0) {
                                // Download the best audio format
                                downloadSelectedAudio((AudioFormat) searchedVideo.bestAudioFormat());
                            } else {
                                // Download the selected audio format
                                downloadSelectedAudio(audioFormats.get(which - 1));
                            }
                        }
                    });
            builder.show();
        } else {
            Toast.makeText(MainActivity.this, "Error retrieving audio formats", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadSelectedAudio(AudioFormat selectedFormat) {
        new AsyncTask<Void, Void, File>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                // Show the progress wheel before starting the download
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected File doInBackground(Void... voids) {
                try {
                    if (searchedVideo != null) {
                        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "IDownloads");
                        RequestVideoFileDownload request = new RequestVideoFileDownload(selectedFormat)
                                .saveTo(outputDir);
                        Response<File> downloadResponse = downloader.downloadVideoFile(request);
                        Log.d("DownloadResponseAudio", "Result: " + downloadResponse.data());
                        Log.d("SelectedAudioFormat", "Result: " + selectedFormat.mimeType());
                        return downloadResponse.data();
                    }
                } catch (Exception e) {
                    Log.e("DownloadAudio", "Error downloading audio", e);
                }

                return null;
            }

            @Override
            protected void onPostExecute(File result) {
                progressBar.setVisibility(View.GONE);
                Log.d("DownloadAudio", "Result: " + result);

                if (result != null) {
                    // Rename the downloaded file with the desired filename
                    File renamedFile = new File(result.getParent(), searchedVideo.details().title());
                    if (result.renameTo(renamedFile)) {
                        // Insert the renamed file into the media store
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, searchedVideo.details().title());
                        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, selectedFormat.mimeType().split(";")[0].trim());

                        Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;  // Use Audio.Media for audio files
                        Uri itemUri = getContentResolver().insert(contentUri, contentValues);

                        try (OutputStream outputStream = getContentResolver().openOutputStream(itemUri)) {
                            if (outputStream != null) {
                                FileInputStream inputStream = new FileInputStream(renamedFile);

                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }

                                inputStream.close();
                                outputStream.close();
                            }
                        } catch (IOException e) {
                            Log.e("DownloadAudio", "Error adding to media store", e);
                        }

                        Toast.makeText(MainActivity.this, "Audio downloaded successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("DownloadAudio", "Error renaming file");
                        Toast.makeText(MainActivity.this, "Error renaming file", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Error downloading Audio", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}
