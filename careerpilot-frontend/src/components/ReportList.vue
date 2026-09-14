<template>
  <section class="report-list">
    <h3>{{ title }}</h3>
    <ul v-if="items.length"><li v-for="item in items" :key="item">{{ item }}</li></ul>
    <p v-else>No supported items.</p>
  </section>
</template>

<script setup>
defineProps({ title: { type: String, required: true }, items: { type: Array, required: true } })
</script>

<style scoped>
.report-list { padding: 16px; border: 1px solid #e4e8f2; border-radius: 11px; }.report-list h3 { margin: 0; font-size: 1rem; }.report-list ul { margin: 10px 0 0; padding-left: 20px; color: #35405a; line-height: 1.55; }.report-list p { margin: 10px 0 0; color: #7c8598; }
</style>
