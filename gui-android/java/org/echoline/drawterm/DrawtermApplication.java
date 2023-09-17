package org.echoline.drawterm;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import java.io.File;

public class DrawtermApplication extends Application {
    private void createNotificationChannels() {
        NotificationChannel channel = new NotificationChannel("0", "Default", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("drawterm");
		NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onCreate(){
        super.onCreate();
        createNotificationChannels();
    }
};
