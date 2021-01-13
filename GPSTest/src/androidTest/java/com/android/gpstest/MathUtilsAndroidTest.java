/*
 * Copyright (C) 2019 Sean J. Barbeau (sjbarbeau@gmail.com)
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
package com.android.gpstest;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.android.gpstest.util.MathUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.UnsupportedEncodingException;

import static junit.framework.Assert.assertEquals;

@RunWith(AndroidJUnit4ClassRunner.class)
public class MathUtilsAndroidTest {

    @Test
    public void testFromBase64() throws UnsupportedEncodingException {
        String input = "VGVzdFN0cmluZw==";
        assertEquals("TestString", MathUtils.fromBase64(input));
    }
}
