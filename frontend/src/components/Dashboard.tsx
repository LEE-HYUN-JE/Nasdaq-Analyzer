import React, { useState, useEffect } from 'react';
import {
  Container,
  Grid,
  Paper,
  Typography,
  Button,
  CircularProgress,
  Box,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import axios from 'axios';

interface StockData {
  symbol: string;
  previousClose: number;
  currentPrice: number;
  changePercent: number;
}

interface MarketAnalysis {
  summary: string;
  timestamp: string;
}

const Item = styled(Paper)(({ theme }) => ({
  backgroundColor: theme.palette.mode === 'dark' ? '#1A2027' : '#fff',
  ...theme.typography.body2,
  padding: theme.spacing(2),
  textAlign: 'center',
  color: theme.palette.text.secondary,
}));

const Dashboard: React.FC = () => {
  const [stocks, setStocks] = useState<StockData[]>([]);
  const [analysis, setAnalysis] = useState<MarketAnalysis | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [stocksResponse, analysisResponse] = await Promise.all([
        axios.get('/api/stocks/nasdaq10'),
        axios.get('/api/analysis/latest'),
      ]);
      setStocks(stocksResponse.data);
      setAnalysis(analysisResponse.data);
    } catch (error) {
      console.error('Error fetching data:', error);
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchData();
  }, []);

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h4" component="h1">
              Nasdaq Market Analysis
            </Typography>
            <Button
              variant="contained"
              color="primary"
              onClick={fetchData}
              disabled={loading}
            >
              {loading ? <CircularProgress size={24} /> : 'Refresh Data'}
            </Button>
          </Box>
        </Grid>

        <Grid item xs={12}>
          <Item>
            <Typography variant="h6" gutterBottom>
              Market Analysis
            </Typography>
            {analysis ? (
              <>
                <Typography variant="body1" paragraph>
                  {analysis.summary}
                </Typography>
                <Typography variant="caption" color="textSecondary">
                  Last updated: {new Date(analysis.timestamp).toLocaleString()}
                </Typography>
              </>
            ) : (
              <Typography>No analysis available</Typography>
            )}
          </Item>
        </Grid>

        <Grid item xs={12}>
          <Item>
            <Typography variant="h6" gutterBottom>
              Nasdaq Top 10 Stocks
            </Typography>
            <Grid container spacing={2}>
              {stocks.map((stock) => (
                <Grid item xs={12} sm={6} md={4} key={stock.symbol}>
                  <Paper elevation={3} sx={{ p: 2 }}>
                    <Typography variant="h6">{stock.symbol}</Typography>
                    <Typography variant="body2">
                      Previous Close: ${stock.previousClose.toFixed(2)}
                    </Typography>
                    <Typography variant="body2">
                      Current Price: ${stock.currentPrice.toFixed(2)}
                    </Typography>
                    <Typography
                      variant="body2"
                      color={stock.changePercent >= 0 ? 'success.main' : 'error.main'}
                    >
                      Change: {stock.changePercent.toFixed(2)}%
                    </Typography>
                  </Paper>
                </Grid>
              ))}
            </Grid>
          </Item>
        </Grid>
      </Grid>
    </Container>
  );
};

export default Dashboard; 