import React, { useState, useEffect } from 'react';
import {
  configStatus,
  onAdminAuthStateChanged,
  signOutAdmin,
} from './config/firebase';
import { isAuthorizedAdmin } from './config/adminAuth';
import LoginScreen from './screens/LoginScreen';
import DashboardScreen from './screens/DashboardScreen';
import FeedbackScreen from './screens/FeedbackScreen';
import MembershipScreen from './screens/MembershipScreen';
import IntegrationsScreen from './screens/IntegrationsScreen';
import SettingsScreen from './screens/SettingsScreen';
import UnauthorizedScreen from './screens/UnauthorizedScreen';
import ConfigRequiredScreen from './screens/ConfigRequiredScreen';
import AdminShell from './components/AdminShell';

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [isAuthLoading, setIsAuthLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('dashboard');

  // 1. Periksa kelayakan konfigurasi Firebase Web
  if (!configStatus.isConfigured) {
    return <ConfigRequiredScreen errors={configStatus.errors} />;
  }

  // 2. Pasang Auth State Listener
  useEffect(() => {
    const unsubscribe = onAdminAuthStateChanged((user) => {
      setCurrentUser(user);
      setIsAuthLoading(false);
    });

    return () => {
      if (typeof unsubscribe === 'function') {
        unsubscribe();
      }
    };
  }, []);

  const handleSignOut = async () => {
    try {
      await signOutAdmin();
      setCurrentUser(null);
    } catch (err) {
      console.error('[App] Gagal keluar:', err);
    }
  };

  // 3. Tampilan Loading Sesi
  if (isAuthLoading) {
    return (
      <div style={styles.loadingWrapper}>
        <div style={styles.loadingBox}>
          <span className="spinner" style={{ width: '28px', height: '28px', borderWidth: '3px' }} />
          <div style={styles.loadingTitle}>Memeriksa Sesi FinSmart Admin...</div>
          <div style={styles.loadingSubtitle}>Menghubungkan ke Firebase Authentication</div>
        </div>
      </div>
    );
  }

  // 4. Belum Masuk: Tampilkan Layar Login
  if (!currentUser) {
    return (
      <LoginScreen
        onLoginSuccess={(user) => {
          setCurrentUser(user);
        }}
      />
    );
  }

  // 5. Sudah Masuk, Tetapi Email Tidak Termasuk Administrator Resmi
  if (!isAuthorizedAdmin(currentUser.email)) {
    return <UnauthorizedScreen user={currentUser} onSignOut={handleSignOut} />;
  }

  // 6. Terotentikasi & Diotorisasi Sebagai Admin Resmi: Tampilkan Admin Shell
  return (
    <AdminShell
      user={currentUser}
      onSignOut={handleSignOut}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      {activeTab === 'dashboard' && <DashboardScreen user={currentUser} onNavigateTab={setActiveTab} />}
      {activeTab === 'feedback' && <FeedbackScreen user={currentUser} />}
      {activeTab === 'membership' && <MembershipScreen user={currentUser} />}
      {activeTab === 'integrations' && <IntegrationsScreen user={currentUser} />}
      {activeTab === 'settings' && <SettingsScreen user={currentUser} />}
    </AdminShell>
  );
}

const styles = {
  loadingWrapper: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'var(--bg-app)',
    color: 'var(--text-primary)',
  },
  loadingBox: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '12px',
    padding: '32px',
    borderRadius: 'var(--radius-xl)',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    boxShadow: 'var(--shadow-md)',
  },
  loadingTitle: {
    fontSize: '0.95rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
  },
  loadingSubtitle: {
    fontSize: '0.8rem',
    color: 'var(--text-secondary)',
  },
};
