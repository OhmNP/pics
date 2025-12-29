import { useEffect, useState, useCallback } from 'react';
import { Box, Typography, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip, IconButton } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import AutoModeIcon from '@mui/icons-material/AutoMode';
import { api, ChangeRecord } from '../services/api';

export default function Changes() {
    const [changes, setChanges] = useState<ChangeRecord[]>([]);
    const [loading, setLoading] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(true);

    const fetchChanges = useCallback(async () => {
        setLoading(true);
        try {
            // Get recent 50 changes
            const response = await api.getChanges({ limit: 50 });
            setChanges(response.data.changes);
        } catch (err) {
            console.error('Failed to fetch changes:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchChanges();
        let interval: any;
        if (autoRefresh) {
            interval = setInterval(fetchChanges, 5000);
        }
        return () => clearInterval(interval);
    }, [autoRefresh, fetchChanges]);

    const getOpColor = (op: string) => {
        switch (op) {
            case 'create': return 'success';
            case 'update': return 'info';
            case 'delete': return 'error';
            default: return 'default';
        }
    };

    return (
        <Box>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <div>
                    <Typography variant="h4" fontWeight="800" className="gradient-text" gutterBottom>
                        Changes Feed
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        Live stream of system mutations
                    </Typography>
                </div>
                <Box display="flex" gap={1}>
                    <IconButton
                        onClick={() => setAutoRefresh(!autoRefresh)}
                        color={autoRefresh ? 'primary' : 'default'}
                        title={autoRefresh ? "Pause Auto-Refresh" : "Enable Auto-Refresh"}
                    >
                        <AutoModeIcon />
                    </IconButton>
                    <IconButton onClick={fetchChanges} disabled={loading} sx={{ color: 'var(--primary)' }}>
                        <RefreshIcon />
                    </IconButton>
                </Box>
            </Box>

            <TableContainer component={Paper} className="glass-panel" sx={{ borderRadius: 3, overflow: 'hidden' }}>
                <Table>
                    <TableHead sx={{ bgcolor: 'rgba(255,255,255,0.05)' }}>
                        <TableRow>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>ID</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Time</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Operation</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>File</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Device</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {changes.map((change) => (
                            <TableRow key={change.change_id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                                <TableCell sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>{change.change_id}</TableCell>
                                <TableCell sx={{ color: 'text.primary' }}>{new Date(change.changed_at).toLocaleString()}</TableCell>
                                <TableCell>
                                    <Chip
                                        label={change.op.toUpperCase()}
                                        size="small"
                                        color={getOpColor(change.op) as any}
                                        variant="outlined"
                                        sx={{ fontWeight: 'bold' }}
                                    />
                                </TableCell>
                                <TableCell sx={{ color: 'text.primary', fontWeight: 500 }}>{change.filename}</TableCell>
                                <TableCell sx={{ color: 'text.secondary' }}>{change.device_id || '-'}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
    );
}
