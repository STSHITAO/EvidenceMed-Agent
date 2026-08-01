const form = document.querySelector('#consult-form');
const image = document.querySelector('#image');
const fileName = document.querySelector('#file-name');
const result = document.querySelector('#result');
const errorBox = document.querySelector('#error');
const submit = document.querySelector('#submit');
let sessionId = null;

image.addEventListener('change', () => {
  fileName.textContent = image.files[0]?.name || '未选择文件（影像可选）';
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  errorBox.classList.add('hidden');
  submit.disabled = true;
  submit.textContent = 'Agent 协作处理中…';
  const body = new FormData();
  body.append('question', document.querySelector('#question').value);
  if (sessionId) body.append('sessionId', sessionId);
  if (image.files[0]) body.append('image', image.files[0]);
  try {
    const response = await fetch('/api/v1/consultations', {method: 'POST', body});
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || '请求失败');
    sessionId = data.sessionId;
    render(data);
  } catch (error) {
    errorBox.textContent = error.message;
    errorBox.classList.remove('hidden');
  } finally {
    submit.disabled = false;
    submit.textContent = '提交给医疗 Agent';
  }
});

function render(data) {
  document.querySelector('#answer').textContent = data.answer;
  const risk = document.querySelector('#risk');
  risk.textContent = `${data.riskLevel}${data.humanReviewRequired ? ' · 需人工复核' : ''}`;
  risk.dataset.level = data.riskLevel;
  const evidence = document.querySelector('#evidence');
  evidence.replaceChildren();
  (data.evidence || []).forEach((item, index) => {
    const card = document.createElement('article');
    const title = document.createElement('strong');
    const content = document.createElement('p');
    title.textContent = `[E${index + 1}] ${item.source}`;
    content.textContent = item.content;
    card.append(title, content);
    evidence.append(card);
  });
  if (!data.evidence?.length) evidence.textContent = '本轮未检索到可展示的知识库证据。';
  document.querySelector('#trace').textContent = `session ${data.sessionId} · trace ${data.traceId}`;
  result.classList.remove('hidden');
}
