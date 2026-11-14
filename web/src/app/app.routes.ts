import { Routes } from '@angular/router';
import { authGuard } from './core/auth';

// Monolito modular: cada feature é um módulo isolado, carregado sob demanda.
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'painel' },
      { path: 'painel', loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'explorar', loadComponent: () => import('./features/explorer/explorer.page').then((m) => m.ExplorerPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
