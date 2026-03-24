<template>
  <div class="min-h-screen bg-gray-50 flex items-center justify-center p-6">
    <div class="text-center max-w-md">
      <!-- Large Database Icon -->
      <div class="inline-flex items-center justify-center w-32 h-32 bg-gray-200 rounded-full mb-8 opacity-20">
        <Database class="w-16 h-16 text-gray-500" />
      </div>

      <!-- Title -->
      <h1 class="text-3xl font-bold text-gray-800 mb-4">合规知识大脑</h1>

      <!-- Description -->
      <p class="text-gray-500 leading-relaxed">
        管理 Agent 的长期记忆与审查准则，包括 RAG 向量切片库和规则引擎字典。
      </p>

      <!-- Feature Cards -->
      <div class="mt-12 grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="bg-white p-5 rounded-xl shadow-sm border border-gray-100 text-left">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center">
              <Brain class="w-5 h-5 text-blue-600" />
            </div>
            <h3 class="font-semibold text-gray-800">RAG 向量库</h3>
          </div>
          <p class="text-sm text-gray-500">医疗监管规则与历史案例向量检索</p>
        </div>

        <div class="bg-white p-5 rounded-xl shadow-sm border border-gray-100 text-left">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-10 h-10 bg-amber-100 rounded-lg flex items-center justify-center">
              <ScrollText class="w-5 h-5 text-amber-600" />
            </div>
            <h3 class="font-semibold text-gray-800">规则引擎</h3>
          </div>
          <p class="text-sm text-gray-500">合规审查规则字典与逻辑配置</p>
        </div>

        <div class="bg-white p-5 rounded-xl shadow-sm border border-gray-100 text-left">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
              <BookOpen class="w-5 h-5 text-green-600" />
            </div>
            <h3 class="font-semibold text-gray-800">案例库</h3>
          </div>
          <p class="text-sm text-gray-500">历史审查案例与处置经验积累</p>
        </div>

        <div class="bg-white p-5 rounded-xl shadow-sm border border-gray-100 text-left">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-10 h-10 bg-purple-100 rounded-lg flex items-center justify-center">
              <Scale class="w-5 h-5 text-purple-600" />
            </div>
            <h3 class="font-semibold text-gray-800">政策库</h3>
          </div>
          <p class="text-sm text-gray-500">医疗监管政策与法规文件管理</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Database, Brain, ScrollText, BookOpen, Scale } from 'lucide-vue-next'
</script>
