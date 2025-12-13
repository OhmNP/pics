
import React, { useState, useEffect } from 'react';
import {
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Typography,
    Box,
    CircularProgress,
    Alert,
    Stack
} from '@mui/material';
import {
    VpnKey as KeyIcon,
    ContentCopy as CopyIcon
} from '@mui/icons-material';
import axios from 'axios';
import { QRCodeCanvas } from 'qrcode.react';
import { api } from '../services/api';

interface PairingProps {
    open: boolean;
    onClose: () => void;
}

const Pairing: React.FC<PairingProps> = ({ open, onClose }) => {
    const [token, setToken] = useState<string | null>(null);
    const [expiresAt, setExpiresAt] = useState<string | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [networkInfo, setNetworkInfo] = useState<{ ips: string[], port: number } | null>(null);

    useEffect(() => {
        if (open) {
            api.getNetworkInfo()
                .then(res => setNetworkInfo(res.data))
                .catch(console.error);
        } else {
            // Reset state on close
            setToken(null);
            setNetworkInfo(null);
        }
    }, [open]);

    const generateToken = async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await axios.post('/api/tokens');
            setToken(response.data.token);
            setExpiresAt(response.data.expiresAt);
        } catch (err: any) { // Type explicitly as any or unknown
            setError(err.response?.data?.error || 'Failed to generate pairing token');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleCopy = () => {
        if (token) {
            navigator.clipboard.writeText(token);
        }
    };

    const handleClose = () => {
        setToken(null);
        setExpiresAt(null);
        setError(null);
        onClose();
    };

    // Construct QR Payload
    const getQrPayload = () => {
        if (!token || !networkInfo || networkInfo.ips.length === 0) return "";
        return JSON.stringify({
            ip: networkInfo.ips[0],
            port: networkInfo.port,
            token: token
        });
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <DialogTitle>Pair New Device</DialogTitle>
            <DialogContent dividers>
                {!token && !loading && (
                    <Box textAlign="center" py={4}>
                        <Typography variant="body1" paragraph>
                            Generate a secure token to pair a new device with this server.
                        </Typography>
                        <Button
                            variant="contained"
                            startIcon={<KeyIcon />}
                            onClick={generateToken}
                            size="large"
                        >
                            Generate Token
                        </Button>
                    </Box>
                )}

                {loading && (
                    <Box display="flex" justifyContent="center" py={4}>
                        <CircularProgress />
                    </Box>
                )}

                {token && (
                    <Box textAlign="center" py={2}>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4} alignItems="center" justifyContent="center">
                            {/* QR Code Section */}
                            <Box>
                                <Box sx={{ p: 2, bgcolor: 'white', borderRadius: 2, display: 'inline-block' }}>
                                    <QRCodeCanvas
                                        value={getQrPayload()}
                                        size={180}
                                        level={"H"}
                                        includeMargin={false}
                                    />
                                </Box>
                                <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 1 }}>
                                    Scan with PhotoSync App
                                </Typography>
                            </Box>

                            {/* Manual Entry Section */}
                            <Box>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Or enter code manually
                                </Typography>
                                <Typography variant="h3" sx={{ fontFamily: 'monospace', letterSpacing: '.1em', my: 2, color: 'primary.main', fontWeight: 'bold' }}>
                                    {token}
                                </Typography>
                                <Button
                                    startIcon={<CopyIcon />}
                                    onClick={handleCopy}
                                    variant="outlined"
                                    size="small"
                                    sx={{ mb: 2 }}
                                >
                                    Copy Code
                                </Button>
                                {networkInfo && (
                                    <Typography variant="body2" color="text.secondary">
                                        Server IP: {networkInfo.ips[0]}
                                    </Typography>
                                )}
                            </Box>
                        </Stack>

                        <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 3 }}>
                            Token expires in 15 minutes
                        </Typography>
                    </Box>
                )}

                {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};

export default Pairing;
