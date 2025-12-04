import { useState, useEffect } from 'react';
import { Box, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Chip, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import { api } from '../services/api';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import SyncIcon from '@mui/icons-material/Sync';

interface Session {
    id: number;
    clientId: number;
    clientName: string;
    startedAt: string;
    endedAt: string | null;
    photosReceived: number;
    status: 'active' | 'completed' | 'failed';
}

function formatDateTime(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}

function getDuration(start: string, end: string | null): string {
    const startDate = new Date(start);
    const endDate = end ? new Date(end) : new Date();
    const seconds = Math.floor((endDate.getTime() - startDate.getTime()) / 1000);

    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}

export default function Sessions() {
    const [sessions, setSessions] = useState<Session[]>([]);
    const [loading, setLoading] = useState(true);
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [clientFilter, setClientFilter] = useState<string>('all');

    useEffect(() => {
        const fetchSessions = async () => {
            try {
                await api.getSessions();
                // Using mock data for UI demo
                const mockSessions: Session[] = [
                    {
                        id: 128,
                        clientId: 1,
                        clientName: 'Pixel 7',
                        startedAt: new Date(Date.now() - 300000).toISOString(),
                        endedAt: new Date(Date.now() - 120000).toISOString(),
                        photosReceived: 45,
                        status: 'completed',
                    },
                    {
                        id: 127,
                        clientId: 2,
                        clientName: 'Galaxy S21',
                        startedAt: new Date(Date.now() - 7200000).toISOString(),
                        endedAt: new Date(Date.now() - 7000000).toISOString(),
                        photosReceived: 32,
                        status: 'completed',
                    },
                    {
                        id: 126,
                        clientId: 1,
                        clientName: 'Pixel 7',
                        startedAt: new Date(Date.now() - 14400000).toISOString(),
                        endedAt: new Date(Date.now() - 14200000).toISOString(),
                        photosReceived: 18,
                        status: 'completed',
                    },
                    {
                        id: 125,
                        clientId: 3,
                        clientName: 'iPhone 13',
                        startedAt: new Date(Date.now() - 21600000).toISOString(),
                        endedAt: new Date(Date.now() - 21400000).toISOString(),
                        photosReceived: 0,
                        status: 'failed',
                    },
                    {
                        id: 124,
                        clientId: 1,
                        clientName: 'Pixel 7',
                        startedAt: new Date(Date.now() - 28800000).toISOString(),
                        endedAt: null,
                        photosReceived: 12,
                        status: 'active',
                    },
                ];
                setSessions(mockSessions);
            } catch (err) {
                console.error('Error fetching sessions:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchSessions();
    }, []);

    const filteredSessions = sessions.filter(session => {
        const matchesStatus = statusFilter === 'all' || session.status === statusFilter;
        const matchesClient = clientFilter === 'all' || session.clientId.toString() === clientFilter;
        return matchesStatus && matchesClient;
    });

    const getStatusChip = (status: Session['status']) => {
        switch (status) {
            case 'completed':
                return <Chip icon={<CheckCircleIcon />} label="Completed" color="success" size="small" />;
            case 'failed':
                return <Chip icon={<ErrorIcon />} label="Failed" color="error" size="small" />;
            case 'active':
                return <Chip icon={<SyncIcon />} label="Active" color="primary" size="small" />;
        }
    };

    return (
        <Box p={3}>
            <Typography variant="h4" fontWeight="bold" gutterBottom>
                Sync Sessions
            </Typography>

            {/* Filters */}
            <Box display="flex" gap={2} mb={3}>
                <FormControl size="small" sx={{ minWidth: 150 }}>
                    <InputLabel>Status</InputLabel>
                    <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} label="Status">
                        <MenuItem value="all">All Status</MenuItem>
                        <MenuItem value="active">Active</MenuItem>
                        <MenuItem value="completed">Completed</MenuItem>
                        <MenuItem value="failed">Failed</MenuItem>
                    </Select>
                </FormControl>

                <FormControl size="small" sx={{ minWidth: 150 }}>
                    <InputLabel>Client</InputLabel>
                    <Select value={clientFilter} onChange={(e) => setClientFilter(e.target.value)} label="Client">
                        <MenuItem value="all">All Clients</MenuItem>
                        <MenuItem value="1">Pixel 7</MenuItem>
                        <MenuItem value="2">Galaxy S21</MenuItem>
                        <MenuItem value="3">iPhone 13</MenuItem>
                    </Select>
                </FormControl>
            </Box>

            {/* Sessions Table */}
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell><strong>Session ID</strong></TableCell>
                            <TableCell><strong>Client</strong></TableCell>
                            <TableCell><strong>Started</strong></TableCell>
                            <TableCell><strong>Duration</strong></TableCell>
                            <TableCell><strong>Photos</strong></TableCell>
                            <TableCell><strong>Status</strong></TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {filteredSessions.map((session) => (
                            <TableRow key={session.id} hover>
                                <TableCell>#{session.id}</TableCell>
                                <TableCell>{session.clientName}</TableCell>
                                <TableCell>{formatDateTime(session.startedAt)}</TableCell>
                                <TableCell>{getDuration(session.startedAt, session.endedAt)}</TableCell>
                                <TableCell>{session.photosReceived}</TableCell>
                                <TableCell>{getStatusChip(session.status)}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>

            {filteredSessions.length === 0 && (
                <Box textAlign="center" py={4}>
                    <Typography color="text.secondary">No sessions found</Typography>
                </Box>
            )}
        </Box>
    );
}
