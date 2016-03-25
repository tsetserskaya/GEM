/*
 * Copyright 2016 Substance Mobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.animbus.music.tasks;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.animbus.music.media.objects.Playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Adrian on 3/25/2016.
 */
public class PlaylistsTask extends LoadTask<Playlist> {
    public PlaylistsTask(Context context, Object... params) {
        super(context, params);
    }

    @Override
    protected List<Playlist> doJob(Object... params) {
        List<Playlist> generated = new ArrayList<>();
        try {
            Cursor playlistsCursor = context.getContentResolver().query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, null, null, null, MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER);

            assert playlistsCursor != null : "Cursor is null";
            int titleColumn = playlistsCursor.getColumnIndex(MediaStore.Audio.Playlists.NAME);
            int idColumn = playlistsCursor.getColumnIndex(MediaStore.Audio.Playlists._ID);

            playlistsCursor.moveToFirst();
            do {
                Playlist playlist = new Playlist();

                String name = playlistsCursor.getString(titleColumn);
                playlist.setName(name);
                playlist.setType(TextUtils.equals(name.toLowerCase(), "favorites") ? 0 : 1);
                playlist.setId(playlistsCursor.getLong(idColumn));

                generated.add(playlist);
                publishProgress(playlist);
            } while (playlistsCursor.moveToNext());
            Collections.sort(generated, new Comparator<Playlist>() {
                @Override
                public int compare(Playlist lhs, Playlist rhs) {
                    return ((Integer) lhs.getType()).compareTo(rhs.getType());
                }
            });
            playlistsCursor.close();
        } catch (IndexOutOfBoundsException e) {
            generated = Collections.emptyList();
        }
        return generated;
    }
}
