// 文本处理逻辑单元测试

// 模拟检测函数
const shouldUseHtml = (content) => {
  const hasEnglish = /[a-zA-Z]/.test(content)
  const hasChinese = /[\u4e00-\u9fa5]/.test(content)
  return hasEnglish
}

// 模拟格式化函数
const formatMessage = (content) => {
  return content
    .replace(/ /g, '&nbsp;')
    .replace(/\n/g, '<br>')
}

// 测试用例
console.log('=== 文本检测测试 ===')

// 测试纯中文
const pureChinese = '你好世界，这是一个测试'
console.log(`纯中文: "${pureChinese}" -> shouldUseHtml: ${shouldUseHtml(pureChinese)} (期望: false)`)

// 测试纯英文
const pureEnglish = 'Hello world, this is a test'
console.log(`纯英文: "${pureEnglish}" -> shouldUseHtml: ${shouldUseHtml(pureEnglish)} (期望: true)`)

// 测试中英混合
const mixedText = 'Hello 你好, world 世界'
console.log(`中英混合: "${mixedText}" -> shouldUseHtml: ${shouldUseHtml(mixedText)} (期望: true)`)

// 测试数字和符号
const numbersOnly = '123 456 789'
console.log(`纯数字: "${numbersOnly}" -> shouldUseHtml: ${shouldUseHtml(numbersOnly)} (期望: false)`)

// 测试带英文单词的中文
const chineseWithEnglishWords = '这个API很好用'
console.log(`中文含英文单词: "${chineseWithEnglishWords}" -> shouldUseHtml: ${shouldUseHtml(chineseWithEnglishWords)} (期望: true)`)

console.log('\n=== 格式化测试 ===')

// 测试英文格式化
const englishWithSpaces = 'Hello world, how are you?'
console.log(`英文原文: "${englishWithSpaces}"`)
console.log(`格式化后: "${formatMessage(englishWithSpaces)}"`)

// 测试英文换行
const englishWithNewlines = 'Hello\nworld\nHow are you?'
console.log(`英文换行原文: "${englishWithNewlines}"`)
console.log(`格式化后: "${formatMessage(englishWithNewlines)}"`)

console.log('\n=== 实际GLM返回模拟测试 ===')

// 模拟GLM可能返回的英文回复
const typicalEnglishReply = 'I understand your question. Based on my knowledge, the answer is: artificial intelligence is a rapidly growing field. What else would you like to know?'
console.log(`典型英文回复: "${typicalEnglishReply}"`)
console.log(`shouldUseHtml: ${shouldUseHtml(typicalEnglishReply)} (期望: true)`)
console.log(`格式化后: "${formatMessage(typicalEnglishReply)}"`)

// 模拟GLM可能返回的中文回复
const typicalChineseReply = '我理解你的问题。根据我的知识，答案是：人工智能是一个快速发展的领域。你还想知道什么？'
console.log(`典型中文回复: "${typicalChineseReply}"`)
console.log(`shouldUseHtml: ${shouldUseHtml(typicalChineseReply)} (期望: false)`)

// 测试一个实际的空格问题案例
const problematicText = 'The quick brown fox jumps over the lazy dog.'
console.log(`问题文本: "${problematicText}"`)
console.log(`shouldUseHtml: ${shouldUseHtml(problematicText)} (期望: true)`)
console.log(`格式化后: "${formatMessage(problematicText)}"`)

// 显示实际HTML渲染效果
console.log('\n=== HTML渲染模拟 ===')
console.log('英文文本HTML输出:')
console.log(`<span>${formatMessage(typicalEnglishReply)}</span>`)
console.log('中文文本HTML输出:')
console.log(`<span>${typicalChineseReply}</span>`)