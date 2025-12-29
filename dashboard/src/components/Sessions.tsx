import { useState, useEffect } from 'react';
import { Box, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Chip, FormControl, InputLabel, Select, MenuItem, Pagination, CircularProgress } from '@mui/material';
import { api, Session } from '../services/api';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import SyncIcon from '@mui/icons-material/Sync';



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
    const [page, setPage] = useState(1);
    const [sessions, setSessions] = useState<Session[]>([]);
    const [loading, setLoading] = useState(true);
    const [totalPages, setTotalPages] = useState(1);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchSessions = async () => {
            setLoading(true);
            try {
                const response = await api.getSessions(page, 10);
                const data = response.data as any;

                if (data.sessions) {
                    setSessions(data.sessions);
                    setTotalPages(data.pagination.pages || 1);
                } else {
                    setSessions([]);
                }
            } catch (err) {
                console.error("Failed to fetch sessions:", err);
                setError("Failed to load sessions");
            } finally {
                setLoading(false);
            }
        };

        fetchSessions();
    }, [page]);

    const handleChangePage = (event: React.ChangeEvent<unknown>, value: number) => {
        setPage(value);
    };

    const getStatusChip = (status: string) => {
        switch (status) {
            case 'completed':
                return <Chip icon={<CheckCircleIcon />} label="Completed" color="success" size="small" />;
            case 'failed':
                return <Chip icon={<ErrorIcon />} label="Failed" color="error" size="small" />;
            case 'active':
                return <Chip icon={<SyncIcon sx={{ animation: 'spin 2s linear infinite' }} />} label="Syncing" color="primary" size="small" />;
            default:
                return <Chip label={status} size="small" />;
        }
    };

    if (loading && sessions.length === 0) {
        return (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
                <CircularProgress />
            </Box>
        );
    }

    if (error) {
        return (
            <Box p={3} textAlign="center">
                <Typography color="error" variant="h6">{error}</Typography>
            </Box>
        );
    }

    return (
        <Box p={3}>
            <Typography variant="h4" fontWeight="bold" gutterBottom>
                Sync Sessions
            </Typography>

            <TableContainer component={Paper} className="glass-panel">
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell><strong>Status</strong></TableCell>
                            <TableCell><strong>Client</strong></TableCell>
                            <TableCell><strong>Started</strong></TableCell>
                            <TableCell><strong>Duration</strong></TableCell>
                            <TableCell><strong>Photos</strong></TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {sessions.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={5} align="center">
                                    <Typography color="text.secondary" py={4}>
                                        No sessions found
                                    </Typography>
                                </TableCell>
                            </TableRow>
                        ) : (
                            sessions.map((session) => (
                                <TableRow key={session.id} hover>
                                    <TableCell>{getStatusChip(session.status)}</TableCell>
                                    <TableCell>{session.clientName || session.deviceId || `Client ${session.clientId}`}</TableCell>
                                    <TableCell>{formatDateTime(session.startedAt)}</TableCell>
                                    <TableCell>{getDuration(session.startedAt, session.endedAt)}</TableCell>
                                    <TableCell>{session.photosReceived}</TableCell>
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
            </TableContainer>

            <Box display="flex" justifyContent="center" mt={3}>
                <Pagination count={totalPages} page={page} onChange={handleChangePage} color="primary" />
            </Box>
        </Box>
    );
}
