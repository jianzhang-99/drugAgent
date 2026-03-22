# Drug Agent Web - Project Documentation

## Overview

Drug Agent Web is a Vue 3 + Vite + Tailwind CSS application designed for drug regulation AI assistance. This is an MVP demo for investors showcasing AI-powered medical compliance review capabilities.

## Tech Stack

- **Frontend Framework**: Vue 3 (Composition API with `<script setup>`)
- **Build Tool**: Vite 5
- **Styling**: Tailwind CSS 3 + Lucide Vue Next icons
- **State Management**: Pinia
- **Routing**: Vue Router 4
- **Icons**: Lucide Vue Next
- **AI Integration**: Gemini API (mock mode for demo)

## Project Structure

```
drug-agent-web/
├── src/
│   ├── assets/          # Static assets and global styles
│   │   └── main.css     # Tailwind CSS imports and custom styles
│   ├── components/      # Reusable Vue components
│   │   ├── layout/      # Layout components
│   │   │   ├── AppSidebar.vue   # Sidebar navigation
│   │   │   ├── AppHeader.vue     # Top header with breadcrumbs
│   │   │   └── TaskPane.vue      # Task center popup panel
│   │   └── workspace/   # Workspace components
│   │       ├── ChatMessage.vue   # Chat message bubble
│   │       ├── QuickActions.vue  # Quick action cards
│   │       ├── ReportCard.vue    # Report summary card
│   │       └── ReportDrawer.vue   # Detailed report drawer
│   ├── router/          # Vue Router configuration
│   │   └── index.js     # Route definitions
│   ├── services/        # API and business services
│   │   ├── gemini.js    # Gemini API client (mock mode)
│   │   └── mock.js      # Mock data generator
│   ├── stores/          # Pinia state management
│   │   ├── session.js   # Session store
│   │   └── task.js      # Task store
│   ├── utils/           # Utility functions
│   │   ├── localStorage.js  # localStorage wrapper
│   │   └── systemPrompt.js  # AI system prompt template
│   ├── views/           # Page components
│   │   ├── WorkspaceView.vue     # Main chat workspace
│   │   ├── TaskBoardView.vue     # Task board page
│   │   ├── KnowledgeBaseView.vue  # Knowledge base page
│   │   └── SettingsView.vue      # Settings page
│   ├── App.vue          # Root component with layout
│   └── main.js          # Application entry point
├── index.html
├── vite.config.js
├── tailwind.config.js
├── postcss.config.js
└── package.json
```

## Routes

| Path          | Component           | Description              |
|---------------|---------------------|-------------------------|
| `/`           | Redirect            | Redirects to /workspace |
| `/workspace`  | WorkspaceView       | Main chat workspace     |
| `/tasks`      | TaskBoardView       | Task management board   |
| `/knowledge`  | KnowledgeBaseView   | Knowledge base search   |
| `/settings`   | SettingsView        | System settings         |

## Core Features

### 1. AI-Powered Review Workspaces
- **Tender Review (标书审查)**: Semantic similarity detection for bid documents
- **Contract Preview (合同预审)**: Risk clause extraction from contracts
- **Compliance Alert (合规预警)**: Anomaly detection in procurement data

### 2. Intelligent Routing
The AI automatically identifies the scene type based on user input and routes to the appropriate workflow.

### 3. Visual Report Generation
- Risk level badges (High/Medium/Low)
- Comprehensive scoring
- Management summaries
- Recommended actions
- Execution trace visualization

### 4. Task Management
- Real-time task progress tracking
- Status indicators (Running/Completed/Pending)
- Task center popup panel

## Development

```bash
# Install dependencies
npm install

# Start dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## AI Integration

The application uses a mock Gemini API service for demo purposes. To connect to a real AI backend:

1. Set `MOCK_MODE: false` in `src/services/gemini.js`
2. Configure your Gemini API endpoint and key
3. The service will automatically route requests to the real API

## Demo Mode

The mock service generates realistic responses including:
- Random risk levels (70% low, 20% medium, 10% high)
- Contextual summaries based on scene type
- Simulated execution delays (1.5-2.5 seconds)

## Investment Highlights

| Capability | Description |
|------------|-------------|
| AI Routing | Automatic scene detection and workflow dispatch |
| Multi-Scenario | Covers tender/contract/compliance review |
| Risk Visualization | Intuitive risk levels and scores |
| Real-time Monitoring | Live task progress tracking |
| Flexible Configuration | Supports multiple AI models |
