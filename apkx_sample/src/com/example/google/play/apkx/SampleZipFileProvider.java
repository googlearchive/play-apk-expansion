
package com.example.google.play.apkx;

/*
 * Copyright (C) 2012 The Android Open Source Project
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
 */
import android.net.Uri;

public class SampleZipFileProvider extends com.google.android.vending.expansion.zipfile.APEZProvider {
    // main content provider URI
    private static final String CONTENT_PREFIX = "content://";

    // must match what is declared in the Zip content provider in
    // the AndroidManifest.xml file
    private static final String AUTHORITY = "com.example.google.play.apkx";

    public static final Uri ASSET_URI = Uri.parse(CONTENT_PREFIX + AUTHORITY);

    public String getAuthority() {
        return AUTHORITY;
    }
}
