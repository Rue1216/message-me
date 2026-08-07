<script setup lang="ts">
import { computed } from 'vue'

import PostFeed from '@/components/post/PostFeed.vue'
import { usePostsByTag } from '@/composables/usePostFeed'

/** 某個標籤底下的發文。 */

// 路由以 props 傳入，型別是字串（來自網址）
const props = defineProps<{ name: string }>()

const tagName = computed(() => props.name)

const feed = usePostsByTag(tagName)
</script>

<template>
  <section>
    <h1 class="mb-4 text-lg font-semibold">
      #{{ tagName }}
    </h1>

    <PostFeed
      :posts="feed.posts.value"
      :is-pending="feed.isPending.value"
      :is-fetching-next-page="feed.isFetchingNextPage.value"
      :has-next-page="feed.hasNextPage.value ?? false"
      :error="feed.error.value"
      empty-title="這個標籤底下還沒有發文"
      empty-description="在發文中輸入 #標籤，就會出現在這裡。"
      :on-load-more="() => feed.fetchNextPage()"
      :on-retry="() => feed.refetch()"
    />
  </section>
</template>
