import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { TextField, Button, Alert, CircularProgress } from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';

export default function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const { login, error, isLoading } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            await login(username, password);
            navigate('/');
        } catch (err) {
            // Error is handled by AuthContext
        }
    };

    return (
        <div style={{
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'var(--bg-primary)',
            position: 'relative',
            overflow: 'hidden'
        }}>
            {/* Background effects */}
            <div style={{
                position: 'absolute',
                top: '-20%',
                right: '-10%',
                width: '600px',
                height: '600px',
                background: 'radial-gradient(circle, var(--primary-glow) 0%, transparent 70%)',
                opacity: 0.15,
                pointerEvents: 'none'
            }} />
            <div style={{
                position: 'absolute',
                bottom: '-10%',
                left: '-5%',
                width: '500px',
                height: '500px',
                background: 'radial-gradient(circle, var(--secondary-glow) 0%, transparent 70%)',
                opacity: 0.15,
                pointerEvents: 'none'
            }} />

            {/* Login card */}
            <div className="glass-panel" style={{
                width: '100%',
                maxWidth: '420px',
                padding: '3rem',
                position: 'relative',
                zIndex: 1
            }}>
                <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
                    <div style={{
                        width: '64px',
                        height: '64px',
                        borderRadius: '16px',
                        background: 'linear-gradient(135deg, var(--primary) 0%, var(--secondary) 100%)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        margin: '0 auto 1.5rem',
                        boxShadow: '0 8px 32px rgba(0, 217, 255, 0.3)'
                    }}>
                        <LockOutlinedIcon style={{ fontSize: '32px', color: 'white' }} />
                    </div>
                    <h1 className="gradient-text" style={{
                        fontSize: '2rem',
                        fontWeight: '800',
                        letterSpacing: '-0.02em',
                        marginBottom: '0.5rem'
                    }}>
                        Welcome Back
                    </h1>
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
                        Sign in to PhotoSync Dashboard
                    </p>
                </div>

                <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
                    {error && (
                        <Alert severity="error" style={{ borderRadius: '12px' }}>
                            {error}
                        </Alert>
                    )}

                    <TextField
                        label="Username"
                        variant="outlined"
                        fullWidth
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        disabled={isLoading}
                        required
                        autoFocus
                        sx={{
                            '& .MuiOutlinedInput-root': {
                                borderRadius: '12px',
                                '&:hover fieldset': {
                                    borderColor: 'var(--primary)'
                                }
                            }
                        }}
                    />

                    <TextField
                        label="Password"
                        type="password"
                        variant="outlined"
                        fullWidth
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        disabled={isLoading}
                        required
                        sx={{
                            '& .MuiOutlinedInput-root': {
                                borderRadius: '12px',
                                '&:hover fieldset': {
                                    borderColor: 'var(--primary)'
                                }
                            }
                        }}
                    />

                    <Button
                        type="submit"
                        variant="contained"
                        fullWidth
                        disabled={isLoading}
                        className="btn-primary"
                        style={{
                            padding: '0.875rem',
                            fontSize: '1rem',
                            fontWeight: 600,
                            borderRadius: '12px',
                            textTransform: 'none',
                            background: 'linear-gradient(135deg, var(--primary) 0%, var(--secondary) 100%)',
                            boxShadow: '0 4px 16px rgba(0, 217, 255, 0.3)',
                            position: 'relative'
                        }}
                    >
                        {isLoading ? (
                            <CircularProgress size={24} style={{ color: 'white' }} />
                        ) : (
                            'Sign In'
                        )}
                    </Button>
                </form>

                <div style={{
                    marginTop: '2rem',
                    padding: '1rem',
                    background: 'rgba(255, 193, 7, 0.1)',
                    border: '1px solid rgba(255, 193, 7, 0.3)',
                    borderRadius: '12px',
                    fontSize: '0.85rem',
                    color: 'var(--text-secondary)'
                }}>
                    <strong style={{ color: '#ffc107' }}>Default Credentials:</strong><br />
                    Username: <code style={{ color: 'var(--primary)' }}>admin</code><br />
                    Password: <code style={{ color: 'var(--primary)' }}>admin123</code>
                </div>
            </div>
        </div>
    );
}
