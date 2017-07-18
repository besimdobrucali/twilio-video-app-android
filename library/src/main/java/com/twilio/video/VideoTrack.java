/*
 * Copyright (C) 2017 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twilio.video;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class VideoTrack implements Track {
    private static final Logger logger = Logger.getLogger(VideoTrack.class);

    private final org.webrtc.VideoTrack webrtcVideoTrack;
    private final String trackId;
    private Map<VideoRenderer, org.webrtc.VideoRenderer> videoRenderersMap = new HashMap<>();
    private boolean isEnabled;
    private boolean isReleased = false;

    /*
     * NOTE
     * This class is a wrapper for org.webrtc.VideoTrack. In order to remove the dependency
     * on this class we would need to re-implement some JNI renderer boilerplate
     */
    VideoTrack(org.webrtc.VideoTrack webRtcVideoTrack, boolean enabled) {
        this.webrtcVideoTrack = webRtcVideoTrack;
        this.trackId = webRtcVideoTrack.id();
        this.isEnabled = enabled;
    }

    /**
     * Add a video renderer to get video from the video track.
     *
     * @param videoRenderer video renderer that receives video.
     */
    public synchronized void addRenderer(@NonNull VideoRenderer videoRenderer) {
        Preconditions.checkNotNull(videoRenderer, "Video renderer must not be null");

        /*
         * We allow for addRenderer to be called after the track has been released to avoid crashes
         * in cases where a developer mistakenly tries to render a track that has been removed.
         * This is different from LocalVideoTrack because developers do not control when a remote
         * video track is released.
         */
        if (!isReleased) {
            org.webrtc.VideoRenderer webrtcVideoRenderer =
                    createWebRtcVideoRenderer(videoRenderer);
            videoRenderersMap.put(videoRenderer, webrtcVideoRenderer);
            webrtcVideoTrack.addRenderer(webrtcVideoRenderer);
        } else {
            logger.w("Attempting to add renderer to track that has been removed");
        }
    }

    /**
     * Remove a video renderer to stop receiving video from the video track.
     *
     * @param videoRenderer the video renderer that should no longer receives video.
     */
    public synchronized void removeRenderer(@NonNull VideoRenderer videoRenderer) {
        Preconditions.checkNotNull(videoRenderer, "Video renderer must not be null");

        /*
         * We allow for removeRenderer to be called after the track has been released to avoid
         * crashes in cases where a developer mistakenly tries to stop renderering a track that has
         * been removed. This is different from LocalVideoTrack because developers do not control
         * when a remote video track is released.
         */
        if (!isReleased) {
            org.webrtc.VideoRenderer webrtcVideoRenderer =
                    videoRenderersMap.remove(videoRenderer);
            if (webrtcVideoTrack != null && webrtcVideoRenderer != null) {
                webrtcVideoTrack.removeRenderer(webrtcVideoRenderer);
            }
        } else {
            logger.w("Attempting to remove renderer from track that has been removed");
        }
    }

    /**
     * The list of renderers receiving video from this video track.
     */
    public List<VideoRenderer> getRenderers() {
        return new ArrayList<>(videoRenderersMap.keySet());
    }

    /**
     * This video track id.
     *
     * @return track id.
     */
    @Override
    public String getTrackId() {
        return trackId;
    }

    /**
     * Check if this video track is enabled.
     *
     * @return true if track is enabled.
     */
    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    private org.webrtc.VideoRenderer createWebRtcVideoRenderer(VideoRenderer videoRenderer) {
        return new org.webrtc.VideoRenderer(new VideoTrack.VideoRendererCallbackAdapter(videoRenderer));
    }

    void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    synchronized void release() {
        if (!isReleased) {
            for (org.webrtc.VideoRenderer videoRenderer : videoRenderersMap.values()) {
                webrtcVideoTrack.removeRenderer(videoRenderer);
            }
            videoRenderersMap.clear();
            isReleased = true;
        }
    }

    private class VideoRendererCallbackAdapter implements org.webrtc.VideoRenderer.Callbacks {
        private final VideoRenderer videoRenderer;

        VideoRendererCallbackAdapter(VideoRenderer videoRenderer) {
            this.videoRenderer = videoRenderer;
        }

        @Override
        public void renderFrame(org.webrtc.VideoRenderer.I420Frame frame) {
            videoRenderer.renderFrame(new I420Frame(frame));
        }
    }
}
