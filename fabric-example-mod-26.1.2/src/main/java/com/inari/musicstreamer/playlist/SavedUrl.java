package com.inari.musicstreamer.playlist;

/**
 * 保存されたURLのエントリ。
 * タグ付けされ、フォルダ（タグ）ごとに管理される。
 */
public record SavedUrl(String url, String title, String tag) {
    public SavedUrl {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be blank");
        }
        if (tag == null || tag.isBlank()) {
            tag = "未分類";
        }
    }

    public SavedUrl withTitle(String newTitle) {
        return new SavedUrl(url, newTitle, tag);
    }

    public SavedUrl withTag(String newTag) {
        return new SavedUrl(url, title, newTag);
    }
}
