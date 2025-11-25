// 调试版 - 检查实际数据
console.log('=== 调试实际应用逻辑 ===')

// 从Vue文件复制的逻辑
const shouldUseHtml = (content) => {
  const hasEnglish = /[a-zA-Z]/.test(content)
  const hasChinese = /[\u4e00-\u9fa5]/.test(content)
  return hasEnglish
}

const formatMessage = (content) => {
  return content
    .replace(/ /g, '&nbsp;')
    .replace(/\n/g, '<br>')
}

// 模拟实际GLM返回的数据
const actualGlmResponses = [
  {
    role: 'assistant',
    content: 'I understand your question. The answer is artificial intelligence.',
    expectedHtml: true
  },
  {
    role: 'assistant', 
    content: '我理解你的问题。答案是人工智能。',
    expectedHtml: false
  },
  {
    role: 'assistant',
    content: 'AI人工智能 is a very interesting field.',
    expectedHtml: true
  },
  {
    role: 'assistant',
    content: 'This is a test with multiple spaces  between words.',
    expectedHtml: true
  }
]

console.log('测试实际GLM回复:')
actualGlmResponses.forEach((msg, index) => {
  const willUseHtml = shouldUseHtml(msg.content)
  const formatted = willUseHtml ? formatMessage(msg.content) : msg.content
  
  console.log(`\n--- 案例 ${index + 1} ---`)
  console.log(`原文: "${msg.content}"`)
  console.log(`期望使用HTML: ${msg.expectedHtml}`)
  console.log(`实际检测: ${willUseHtml}`)
  console.log(`格式化结果: "${formatted}"`)
  console.log(`检测结果正确: ${willUseHtml === msg.expectedHtml}`)
  
  // 模拟Vue渲染
  if (willUseHtml) {
    console.log(`Vue将渲染: <span>${formatted}</span>`)
  } else {
    console.log(`Vue将渲染: <span>${msg.content}</span>`)
  }
})

// 特殊测试：检查为什么可能不生效
console.log('\n=== 特殊问题检查 ===')

// 1. 检查空格是否被正确替换
const spaceTest = 'Hello world'
console.log(`空格测试: "${spaceTest}" -> "${formatMessage(spaceTest)}"`)

// 2. 检查多个空格
const multiSpaceTest = 'Hello   world'
console.log(`多空格测试: "${multiSpaceTest}" -> "${formatMessage(multiSpaceTest)}"`)

// 3. 检查换行
const newlineTest = 'Hello\nworld'
console.log(`换行测试: "${newlineTest}" -> "${formatMessage(newlineTest)}"`)

// 4. 检查特殊字符
const specialCharTest = 'Hello & world'
console.log(`特殊字符测试: "${specialCharTest}" -> "${formatMessage(specialCharTest)}"`)

console.log('\n=== 可能的问题诊断 ===')
console.log('1. 如果英文文本仍然没有空格，可能原因:')
console.log('   - CSS样式覆盖了HTML格式')
console.log('   - Vue的v-html指令没有正确执行')
console.log('   - 浏览器缓存问题')
console.log('   - 后端返回的数据被额外处理了')

console.log('\n2. 建议检查:')
console.log('   - 浏览器开发者工具中实际渲染的HTML')
console.log('   - Vue组件中的shouldUseHtml函数是否正确调用')
console.log('   - CSS中是否有冲突的样式规则')