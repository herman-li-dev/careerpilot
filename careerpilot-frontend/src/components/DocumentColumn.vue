<template>
  <section class="document-column">
    <div class="column-heading">
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </div>
    <form v-if="!readOnly" class="document-form" @submit.prevent="submit">
      <label>
        Title
        <input v-model="form.title" type="text" maxlength="160" :placeholder="itemName + ' title'" required />
      </label>
      <label>
        English text
        <textarea v-model="form.rawText" rows="8" :placeholder="'Paste your ' + itemName.toLowerCase() + ' here.'" required></textarea>
      </label>
      <button class="primary-button" type="submit" :disabled="!canSubmit || saving">
        {{ saving ? 'Saving…' : 'Save ' + itemName }}
      </button>
    </form>
    <form v-if="uploadDocument && !readOnly" class="upload-form" @submit.prevent="submitUpload">
      <div class="form-divider"><span>or upload</span></div>
      <label>
        PDF or DOCX file
        <input ref="fileInput" type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" required @change="selectFile" />
      </label>
      <label>
        Upload title <span class="optional-label">Optional</span>
        <input v-model="uploadTitle" type="text" maxlength="160" placeholder="Uploaded resume" />
      </label>
      <p class="upload-help">Maximum 5 MiB. Scanned PDFs and OCR are not supported. The original file is not stored.</p>
      <button class="secondary-button" type="submit" :disabled="!selectedFile || uploading">
        {{ uploading ? 'Uploading…' : 'Upload Resume' }}
      </button>
    </form>
    <div class="document-list">
      <p v-if="!items.length" class="empty-state">No saved {{ title.toLowerCase() }} yet.</p>
      <article v-for="item in items" :key="item.id" class="document-card">
        <button class="document-open" type="button" @click="$emit('open', item)">
          <strong>{{ item.title }}</strong>
          <span>{{ formatDate(item.updatedAt) }}</span>
        </button>
        <div class="card-footer">
          <span class="status" :class="item.parseStatus.toLowerCase()">{{ formatStatus(item.parseStatus) }}</span>
          <button v-if="!readOnly && (item.parseStatus === 'NOT_STARTED' || item.parseStatus === 'FAILED')" class="text-button" type="button" :disabled="busyId === item.id" @click="$emit('parse', item)">
            {{ busyId === item.id ? 'Parsing…' : item.parseStatus === 'FAILED' ? 'Retry parse' : 'Parse' }}
          </button>
        </div>
        <p v-if="item.parseError" class="parse-error">{{ item.parseError }}</p>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
  itemName: { type: String, required: true },
  items: { type: Array, required: true },
  busyId: { type: Number, default: null },
  readOnly: { type: Boolean, default: false },
  createDocument: { type: Function, required: true },
  uploadDocument: { type: Function, default: null }
})

defineEmits(['open', 'parse'])

const form = reactive({ title: '', rawText: '' })
const saving = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)
const uploadTitle = ref('')
const fileInput = ref(null)
const canSubmit = computed(() => form.title.trim() && form.rawText.trim())

async function submit() {
  if (!canSubmit.value || saving.value) return
  saving.value = true
  try {
    await props.createDocument({ title: form.title.trim(), rawText: form.rawText })
    form.title = ''
    form.rawText = ''
  } catch {
    // The parent displays the safe API error and keeps the user's pasted text in place.
  } finally {
    saving.value = false
  }
}

function selectFile(event) {
  selectedFile.value = event.target.files?.[0] || null
}

async function submitUpload() {
  if (!selectedFile.value || uploading.value || !props.uploadDocument) return
  uploading.value = true
  try {
    await props.uploadDocument({ file: selectedFile.value, title: uploadTitle.value.trim() })
    selectedFile.value = null
    uploadTitle.value = ''
    if (fileInput.value) fileInput.value.value = ''
  } catch {
    // The parent displays the safe API error and keeps the selected file available for retry.
  } finally {
    uploading.value = false
  }
}

function formatStatus(status) {
  return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase())
}

function formatDate(value) {
  return new Intl.DateTimeFormat('en-CA', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
</script>

<style scoped>
.document-column { padding: 24px; background: #fff; border: 1px solid #e3e6f0; border-radius: 16px; }
.column-heading { min-height: 68px; }.column-heading h3 { margin: 0; font-size: 1.2rem; }.column-heading p, .document-open span { color: #64708a; }.column-heading p { margin: 7px 0 0; }
.document-form { display: grid; gap: 16px; margin: 26px 0 18px; } label { display: grid; gap: 7px; color: #35405a; font-size: .9rem; font-weight: 700; } input, textarea { width: 100%; padding: 11px 12px; border: 1px solid #cdd4e4; border-radius: 9px; background: #fff; color: #182033; font: inherit; } textarea { resize: vertical; line-height: 1.5; } input:focus, textarea:focus { outline: 3px solid rgba(89, 103, 216, .18); border-color: #5967d8; }
.upload-form { display: grid; gap: 14px; margin: 4px 0 22px; }.form-divider { display: flex; align-items: center; gap: 10px; color: #7c8598; font-size: .75rem; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }.form-divider::before, .form-divider::after { content: ''; height: 1px; flex: 1; background: #e3e6f0; }.optional-label { color: #7c8598; font-size: .75rem; font-weight: 600; }.upload-help { margin: -4px 0 0; color: #64708a; font-size: .8rem; line-height: 1.45; }
.primary-button, .secondary-button, .text-button { border: 0; border-radius: 9px; font: inherit; font-weight: 750; }.primary-button { padding: 11px 15px; background: #5967d8; color: white; }.primary-button:hover:not(:disabled) { background: #4655c9; }.secondary-button { padding: 10px 14px; background: #e9ecff; color: #4050b6; }.secondary-button:hover:not(:disabled) { background: #dfe4ff; }.text-button { padding: 4px 0; background: transparent; color: #4d5bc8; text-align: left; }.text-button:hover:not(:disabled) { color: #2739a8; text-decoration: underline; } button { cursor: pointer; } button:disabled { cursor: not-allowed; opacity: .55; }
.document-list { display: grid; gap: 12px; }.empty-state { padding: 20px 0; color: #7c8598; text-align: center; }.document-card { border: 1px solid #e4e8f2; border-radius: 11px; padding: 14px; }.document-open { width: 100%; display: grid; gap: 5px; border: 0; padding: 0; background: transparent; color: #1c2741; text-align: left; }.document-open:hover strong { color: #5967d8; }.document-open span { font-size: .82rem; }.card-footer { display: flex; justify-content: space-between; gap: 20px; align-items: center; margin-top: 13px; }.status { display: inline-flex; padding: 4px 8px; border-radius: 999px; background: #edf0f7; color: #52607a; font-size: .72rem; font-weight: 800; }.status.completed { color: #137a4a; background: #e4f6ed; }.status.failed { color: #a44917; background: #fff0e8; }.status.running { color: #3c51b5; background: #e9ecff; }.parse-error { margin: 10px 0 0; color: #a44917; font-size: .82rem; }
</style>
