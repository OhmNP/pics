
import React, { useState } from 'react';
import {
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Typography,
    Box,
    CircularProgress,
    Alert
} from '@mui/material';
import {
    VpnKey as KeyIcon,
    ContentCopy as CopyIcon
} from '@mui/icons-material';
import axios from 'axios';

interface PairingProps {
    open: boolean;
    onClose: () => void;
}

const Pairing: React.FC<PairingProps> = ({ open, onClose }) => {
    const [token, setToken] = useState<string | null>(null);
    const [expiresAt, setExpiresAt] = useState<string | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    const generateToken = async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await axios.post('/api/tokens');
            setToken(response.data.token);
            setExpiresAt(response.data.expiresAt);
        } catch (err) {
            setError('Failed to generate pairing token');
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

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
            <DialogTitle>Pair New Device</DialogTitle>
            <DialogContent dividers>
                {!token && !loading && (
                    <Box textAlign="center" py={2}>
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
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Enter this code on your device
                        </Typography>
                        <Typography variant="h2" sx={{ fontFamily: 'monospace', letterSpacing: '.1em', my: 2, color: 'primary.main' }}>
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
                        <Typography variant="caption" display="block" color="text.secondary">
                            Expires in 15 minutes
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
