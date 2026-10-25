import React from 'react';
import ReactDOM from 'react-dom/client';
import { VisitorApp } from './VisitorApp';
import '../style.css';
import './visitor.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <VisitorApp />
  </React.StrictMode>
);
