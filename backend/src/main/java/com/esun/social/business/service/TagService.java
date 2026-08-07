package com.esun.social.business.service;

import com.esun.social.business.model.Tag;
import com.esun.social.data.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 標籤的查詢。
 *
 * <p>標籤沒有獨立的寫入路徑：它們只在發文被建立、編輯或刪除時，於同一個交易內
 * 隨之產生與更新（見 {@code sp_post_create / update / delete}）。
 * 這是刻意的設計——標籤是發文內容的衍生物，不是可以獨立編輯的資源。
 */
@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /** 熱門標籤，依使用次數由多到少。 */
    public List<Tag> listPopular(int limit) {
        return tagRepository.findPopular(limit);
    }
}
