import React from 'react';
import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PhotoLibraryIcon from '@mui/icons-material/PhotoLibrary';
import PeopleIcon from '@mui/icons-material/People';
import SyncIcon from '@mui/icons-material/Sync';
import SettingsIcon from '@mui/icons-material/Settings';
import Dashboard from './components/Dashboard';
import Photos from './components/Photos';
import Clients from './components/Clients';
import Sessions from './components/Sessions';
import Settings from './components/Settings';

// Create a dark theme instance to ensure MUI components play nice with our custom CSS
const darkTheme = createTheme({
    palette: {
        mode: 'dark',
        primary: { main: '#00d9ff' },
        background: { default: 'transparent', paper: '#1a1d2e' },
    },
    typography: { fontFamily: 'Inter, sans-serif' },
});

interface NavItem {
    text: string;
    icon: React.ReactNode;
    path: string;
}

const navItems: NavItem[] = [
    { text: 'Dashboard', icon: <DashboardIcon />, path: '/' },
    { text: 'Photos', icon: <PhotoLibraryIcon />, path: '/photos' },
    { text: 'Clients', icon: <PeopleIcon />, path: '/clients' },
    { text: 'Sessions', icon: <SyncIcon />, path: '/sessions' },
    { text: 'Settings', icon: <SettingsIcon />, path: '/settings' },
];

function Navigation() {
    const navigate = useNavigate();
    const location = useLocation();

    return (
        <nav style={{
            width: '260px',
            background: 'var(--bg-panel)',
            borderRight: '1px solid var(--border-subtle)',
            display: 'flex',
            flexDirection: 'column',
            padding: '2rem 1rem',
            height: '100vh',
            position: 'fixed',
            left: 0,
            top: 0
        }}>
            <div style={{ marginBottom: '3rem', padding: '0 1rem' }}>
                <h1 className="gradient-text" style={{ fontSize: '1.5rem', fontWeight: '800', letterSpacing: '-0.02em' }}>
                    PhotoSync
                </h1>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                    Professional Suite
                </p>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {navItems.map((item) => {
                    const isActive = location.pathname === item.path;
                    return (
                        <div
                            key={item.path}
                            onClick={() => navigate(item.path)}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '1rem',
                                padding: '0.875rem 1rem',
                                borderRadius: '12px',
                                cursor: 'pointer',
                                transition: 'all 0.2s ease',
                                background: isActive ? 'rgba(0, 217, 255, 0.1)' : 'transparent',
                                color: isActive ? 'var(--primary)' : 'var(--text-secondary)',
                                borderLeft: isActive ? '3px solid var(--primary)' : '3px solid transparent'
                            }}
                        >
                            <span style={{ display: 'flex', alignItems: 'center' }}>
                                {item.icon}
                            </span>
                            <span style={{ fontWeight: isActive ? 600 : 500 }}>{item.text}</span>
                        </div>
                    );
                })}
            </div>
        </nav>
    );
}

function AppContent() {
    return (
        <div style={{ display: 'flex', minHeight: '100vh' }}>
            <Navigation />
            <main style={{
                flex: 1,
                marginLeft: '260px',
                padding: '2rem 3rem',
                minHeight: '100vh',
                position: 'relative'
            }}>
                <div style={{
                    position: 'absolute',
                    top: '-20%',
                    right: '-10%',
                    width: '600px',
                    height: '600px',
                    background: 'radial-gradient(circle, var(--primary-glow) 0%, transparent 70%)',
                    opacity: 0.15,
                    pointerEvents: 'none',
                    zIndex: 0
                }} />
                <div style={{
                    position: 'absolute',
                    bottom: '-10%',
                    left: '-5%',
                    width: '500px',
                    height: '500px',
                    background: 'radial-gradient(circle, var(--secondary-glow) 0%, transparent 70%)',
                    opacity: 0.15,
                    pointerEvents: 'none',
                    zIndex: 0
                }} />

                <div style={{ position: 'relative', zIndex: 1 }}>
                    <Routes>
                        <Route path="/" element={<Dashboard />} />
                        <Route path="/photos" element={<Photos />} />
                        <Route path="/clients" element={<Clients />} />
                        <Route path="/sessions" element={<Sessions />} />
                        <Route path="/settings" element={<Settings />} />
                    </Routes>
                </div>
            </main>
        </div>
    );
}

export default function App() {
    return (
        <ThemeProvider theme={darkTheme}>
            <BrowserRouter>
                <AppContent />
            </BrowserRouter>
        </ThemeProvider>
    );
}
