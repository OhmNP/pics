import React, { useState, useEffect } from 'react';
import {
    Box,
    Paper,
    Typography,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Chip,
    IconButton,
    InputBase,
    Pagination,
    Stack,
    CircularProgress,
    Alert
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import RefreshIcon from '@mui/icons-material/Refresh';
import FilterListIcon from '@mui/icons-material/FilterList';
import { useAuth } from '../contexts/AuthContext';

interface AuditLog {
    id: number;
    userId: number;
    username: string;
    action: string;
    targetType: string;
    targetId: string;
    details: string;
    timestamp: string;
    ipAddress: string;
}

interface PaginationData {
    page: number;
    limit: number;
    total: number;
    pages: number;
}

export default function AuditLogs() {
    const [logs, setLogs] = useState<AuditLog[]>([]);
    const [pagination, setPagination] = useState<PaginationData>({ page: 1, limit: 20, total: 0, pages: 0 });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [page, setPage] = useState(1);
    const { sessionToken } = useAuth();

    const fetchLogs = async (pageNum: number) => {
        setLoading(true);
        setError(null);
        try {
            const response = await fetch(`http://localhost:3000/api/audit?page=${pageNum}&limit=20`, {
                headers: {
                    'Authorization': `Bearer ${sessionToken}`
                }
            });

            if (!response.ok) {
                throw new Error('Failed to fetch audit logs');
            }

            const data = await response.json();
            if (data.error) {
                throw new Error(data.error);
            }

            // Handle both "items" (from my implementation) or "logs" (from initial plan)
            // My implementation in ApiServer.cpp line 1144 uses "items".
            setLogs(data.items || data.logs || []);
            setPagination(data.pagination);
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchLogs(page);
    }, [page, sessionToken]);

    const handlePageChange = (event: React.ChangeEvent<unknown>, value: number) => {
        setPage(value);
    };

    const getActionColor = (action: string) => {
        switch (action) {
            case 'LOGIN': return 'success';
            case 'LOGOUT': return 'default';
            case 'GENERATE_TOKEN': return 'info';
            case 'REGENERATE_THUMBNAILS': return 'warning';
            case 'DELETE': return 'error';
            default: return 'primary';
        }
    };

    return (
        <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
                <div>
                    <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }} className="gradient-text">
                        Audit Logs
                    </Typography>
                    <Typography variant="body1" sx={{ color: 'text.secondary' }}>
                        Track system activity and security events
                    </Typography>
                </div>
                <IconButton
                    onClick={() => fetchLogs(page)}
                    sx={{
                        color: 'primary.main',
                        bgcolor: 'rgba(0, 217, 255, 0.1)',
                        '&:hover': { bgcolor: 'rgba(0, 217, 255, 0.2)' }
                    }}
                >
                    <RefreshIcon />
                </IconButton>
            </Box>

            {error && (
                <Alert severity="error" sx={{ mb: 3 }}>
                    {error}
                </Alert>
            )}

            <Paper sx={{
                bgcolor: 'background.paper',
                borderRadius: '16px',
                border: '1px solid var(--border-subtle)',
                overflow: 'hidden'
            }}>
                <TableContainer>
                    <Table>
                        <TableHead>
                            <TableRow sx={{ bgcolor: 'rgba(255,255,255,0.02)' }}>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Time</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>User</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Action</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Target</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Details</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>IP Address</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {loading ? (
                                <TableRow>
                                    <TableCell colSpan={6} align="center" sx={{ py: 8 }}>
                                        <CircularProgress size={40} />
                                    </TableCell>
                                </TableRow>
                            ) : logs.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={6} align="center" sx={{ py: 8, color: 'text.secondary' }}>
                                        No audit logs found.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                logs.map((log) => (
                                    <TableRow key={log.id} hover sx={{ '&:hover': { bgcolor: 'rgba(255,255,255,0.02)' } }}>
                                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                                            {new Date(log.timestamp + 'Z').toLocaleString()}
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                                {log.username}
                                            </Typography>
                                            <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.7rem' }}>
                                                ID: {log.userId}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                label={log.action}
                                                size="small"
                                                color={getActionColor(log.action) as any}
                                                sx={{ fontWeight: 600, fontSize: '0.75rem', height: '24px' }}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2">
                                                {log.targetType}
                                            </Typography>
                                            {log.targetId && (
                                                <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
                                                    {log.targetId}
                                                </Typography>
                                            )}
                                        </TableCell>
                                        <TableCell sx={{ color: 'text.secondary', maxWidth: '300px' }}>
                                            {log.details}
                                        </TableCell>
                                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                                            {log.ipAddress || '—'}
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>

                <Box sx={{ p: 2, display: 'flex', justifyContent: 'flex-end', borderTop: '1px solid var(--border-subtle)' }}>
                    <Pagination
                        count={pagination.pages}
                        page={page}
                        onChange={handlePageChange}
                        color="primary"
                        shape="rounded"
                    />
                </Box>
            </Paper>
        </Box>
    );
}
