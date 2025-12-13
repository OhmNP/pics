import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { api } from '../services/api';

interface User {
    id: number;
    username: string;
}

interface AuthContextType {
    user: User | null;
    sessionToken: string | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (username: string, password: string) => Promise<void>;
    logout: () => Promise<void>;
    error: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [sessionToken, setSessionToken] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Check for existing session on mount
    useEffect(() => {
        const token = localStorage.getItem('sessionToken');
        if (token) {
            validateSession(token);
        } else {
            setIsLoading(false);
        }
    }, []);

    const validateSession = async (token: string) => {
        try {
            const response = await api.validateSession(token);
            if (response.data.valid) {
                setSessionToken(token);
                // Get user info from token (you might want to store this separately)
                const userStr = localStorage.getItem('user');
                if (userStr) {
                    setUser(JSON.parse(userStr));
                }
            } else {
                // Session invalid or expired
                localStorage.removeItem('sessionToken');
                localStorage.removeItem('user');
                setSessionToken(null);
                setUser(null);
            }
        } catch (err) {
            console.error('Session validation failed:', err);
            localStorage.removeItem('sessionToken');
            localStorage.removeItem('user');
            setSessionToken(null);
            setUser(null);
        } finally {
            setIsLoading(false);
        }
    };

    const login = async (username: string, password: string) => {
        setError(null);
        setIsLoading(true);
        try {
            const response = await api.login(username, password);
            const { sessionToken: token, user: userData } = response.data;

            // Store session
            localStorage.setItem('sessionToken', token);
            localStorage.setItem('user', JSON.stringify(userData));
            setSessionToken(token);
            setUser(userData);
        } catch (err: any) {
            const errorMessage = err.response?.data?.error || 'Login failed';
            setError(errorMessage);
            throw new Error(errorMessage);
        } finally {
            setIsLoading(false);
        }
    };

    const logout = async () => {
        setIsLoading(true);
        try {
            if (sessionToken) {
                await api.logout(sessionToken);
            }
        } catch (err) {
            console.error('Logout failed:', err);
        } finally {
            // Clear local state regardless of API call success
            localStorage.removeItem('sessionToken');
            localStorage.removeItem('user');
            setSessionToken(null);
            setUser(null);
            setIsLoading(false);
        }
    };

    return (
        <AuthContext.Provider
            value={{
                user,
                sessionToken,
                isAuthenticated: !!user && !!sessionToken,
                isLoading,
                login,
                logout,
                error
            }}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
}
