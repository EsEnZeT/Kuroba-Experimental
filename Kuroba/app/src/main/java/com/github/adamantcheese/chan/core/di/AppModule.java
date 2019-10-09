/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.di;

import android.app.NotificationManager;
import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.core.net.BitmapLruImageCache;
import com.github.adamantcheese.chan.core.saver.ImageSaver;
import com.github.adamantcheese.chan.ui.captcha.CaptchaHolder;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

import static android.content.Context.NOTIFICATION_SERVICE;

public class AppModule {
    private Context applicationContext;
    public static final String DI_TAG = "Dependency Injection";

    public AppModule(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Provides
    @Singleton
    public Context provideApplicationContext() {
        Logger.d(DI_TAG, "App Context");
        return applicationContext;
    }

    @Provides
    @Singleton
    public ImageLoaderV2 provideImageLoaderV2(RequestQueue requestQueue) {
        final int runtimeMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int lruImageCacheSize = runtimeMemory / 8;
        ImageLoader imageLoader = new ImageLoader(
                requestQueue,
                new BitmapLruImageCache(lruImageCacheSize));
        Logger.d(DI_TAG, "Image loader v2");
        return new ImageLoaderV2(imageLoader);
    }

    @Provides
    @Singleton
    public NotificationManager provideNotificationManager() {
        Logger.d(DI_TAG, "Notification manager");
        return (NotificationManager) applicationContext.getSystemService(NOTIFICATION_SERVICE);
    }

    @Provides
    @Singleton
    public ThemeHelper provideThemeHelper() {
        Logger.d(DI_TAG, "Theme helper");
        return new ThemeHelper();
    }

    @Provides
    @Singleton
    public ImageSaver provideImageSaver() {
        Logger.d(DI_TAG, "Image saver");
        return new ImageSaver();
    }

    @Provides
    @Singleton
    public CaptchaHolder provideCaptchaHolder() {
        Logger.d(DI_TAG, "Captcha holder");
        return new CaptchaHolder();
    }
}
