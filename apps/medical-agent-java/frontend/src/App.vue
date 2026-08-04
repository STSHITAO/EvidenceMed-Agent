<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'EMERGENCY'

interface Evidence {
  source: string
  content: string
}

interface ConsultationResponse {
  sessionId: string
  traceId: string
  answer: string
  riskLevel: RiskLevel
  humanReviewRequired: boolean
  safetyReasons: string[]
  evidence: Evidence[]
}

const question = ref('')
const image = ref<File | null>(null)
const sessionId = ref<string | null>(null)
const response = ref<ConsultationResponse | null>(null)
const submitting = ref(false)
const status = ref('')
const error = ref('')
const result = ref<HTMLElement | null>(null)

const fileLabel = computed(() => image.value
  ? `${image.value.name} · ${(image.value.size / (1024 * 1024)).toFixed(1)} MB`
  : '未选择影像')

const riskLabel = computed(() => {
  const labels: Record<RiskLevel, string> = {
    LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', EMERGENCY: '紧急风险'
  }
  return response.value ? `${labels[response.value.riskLevel]}${response.value.humanReviewRequired ? ' · 人工复核' : ''}` : ''
})

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  image.value = input.files?.[0] ?? null
}

function resetSession() {
  question.value = ''
  image.value = null
  sessionId.value = null
  response.value = null
  status.value = '已开启新的咨询会话。'
  error.value = ''
}

async function submitConsultation() {
  error.value = ''
  if (image.value && image.value.size > 20 * 1024 * 1024) {
    error.value = '影像超过 20 MB 限制，请压缩后重新提交。'
    return
  }
  submitting.value = true
  status.value = '处理中：病例上下文、证据检索、回答生成和安全审查依次执行。'
  const body = new FormData()
  body.append('question', question.value.trim())
  if (sessionId.value) body.append('sessionId', sessionId.value)
  if (image.value) body.append('image', image.value)
  try {
    const httpResponse = await fetch('/api/v1/consultations', { method: 'POST', body })
    const payload = await httpResponse.json() as ConsultationResponse & { message?: string }
    if (!httpResponse.ok) throw new Error(payload.message || '请求处理失败，请稍后重试。')
    response.value = payload
    sessionId.value = payload.sessionId
    status.value = payload.humanReviewRequired ? '结果已标记为需要人工复核。' : '安全审查完成，可查看辅助结果。'
    await nextTick()
    result.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '请求处理失败，请稍后重试。'
    status.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="shell">
    <header class="hero">
      <div class="brand-line"><span class="brand-mark">E</span><strong>EvidenceMed</strong><span class="live-dot">安全复核工作台</span></div>
      <div class="hero-copy">
        <p class="eyebrow">MULTIMODAL EVIDENCE · CLINICIAN REVIEW</p>
        <h1>让医学判断<br><em>有据可循</em></h1>
        <p>上传 JPEG/PNG 影像并描述问题。系统会检索知识库、生成辅助分析，并标记必须由临床人员复核的高风险结果。</p>
      </div>
      <aside class="safety-note"><strong>使用边界</strong><span>仅供科研、教学和医生辅助复核；不替代诊断、处方或急诊处置。</span></aside>
    </header>

    <section class="workspace" aria-label="医学咨询工作区">
      <section class="panel consultation-panel">
        <div class="section-heading"><div><p class="step">01 · 提交资料</p><h2>发起辅助咨询</h2></div><span class="optional">影像可选</span></div>
        <form @submit.prevent="submitConsultation">
          <label for="question">咨询问题</label>
          <textarea id="question" v-model="question" minlength="2" maxlength="3000" required placeholder="例如：请说明这张胸部影像中需要由医生重点复核的表现、证据依据和下一步建议。" />
          <div class="field-meta"><span>{{ question.length }} / 3000</span><span>请勿填写不必要的身份信息</span></div>
          <div class="upload-row">
            <label class="file-picker" for="image"><span class="upload-icon">＋</span><span><strong>选择影像</strong><small>仅 JPEG 或 PNG，最大 20 MB</small></span></label>
            <input id="image" type="file" accept="image/jpeg,image/png" @change="selectImage" />
            <span class="file-name">{{ fileLabel }}</span>
          </div>
          <div class="form-actions">
            <button type="submit" :disabled="submitting"><span>{{ submitting ? '正在检索证据并执行安全审查…' : '开始证据复核' }}</span><span>→</span></button>
            <button class="secondary" type="button" @click="resetSession">新建咨询</button>
          </div>
        </form>
        <p class="request-status" aria-live="polite">{{ status }}</p>
      </section>

      <aside class="panel process-panel"><p class="step">02 · 处理路径</p><h2>每次回答都经过安全审查</h2>
        <ol class="process-list"><li><span>01</span><div><strong>病例上下文</strong><small>仅用于当前受控会话</small></div></li><li><span>02</span><div><strong>检索医学证据</strong><small>BM25、向量检索与重排序</small></div></li><li><span>03</span><div><strong>生成辅助分析</strong><small>区分证据与不确定性</small></div></li><li><span>04</span><div><strong>安全与人工复核</strong><small>高风险结果不展示未核准草稿</small></div></li></ol>
      </aside>
    </section>

    <section v-if="response" ref="result" class="result" aria-live="polite">
      <div class="result-title"><div><p class="step">03 · 复核结果</p><h2>辅助分析与证据</h2></div><span class="risk" :data-level="response.riskLevel">{{ riskLabel }}</span></div>
      <div v-if="response.humanReviewRequired" class="review-hold"><strong>已进入人工复核</strong><span>{{ response.safetyReasons.length ? response.safetyReasons.join('；') : '请由临床人员完成复核。' }}</span></div>
      <div class="result-grid"><article class="panel answer-panel"><h3>辅助回答</h3><div class="answer">{{ response.answer || '系统未生成可展示的辅助回答。' }}</div></article><article class="panel evidence-panel"><h3>引用证据</h3><div v-if="response.evidence.length" class="evidence"><article v-for="(item, index) in response.evidence" :key="`${item.source}-${index}`"><span>E{{ index + 1 }}</span><strong>{{ item.source || '未命名来源' }}</strong><p>{{ item.content || '无可展示摘要' }}</p></article></div><p v-else class="empty-evidence">当前未检索到可展示的知识库证据。</p></article></div>
      <footer class="trace-row"><span>本页不会保存原始影像</span><span>会话 {{ response.sessionId }} · 审计 {{ response.traceId }}</span></footer>
    </section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </main>
</template>
