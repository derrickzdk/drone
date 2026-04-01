let intervalId = null;
let checkInterval = 1000;
let isRunning = false;
let lastTickTime = 0;
let tickCount = 0;

self.onmessage = function(e) {
    const { type, payload } = e.data;
    
    if (type === 'START') {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        lastTickTime = Date.now();
        tickCount = 0;
        
        function check() {
            if (!isRunning) {
                return;
            }
            
            const currentTime = Date.now();
            const elapsed = currentTime - lastTickTime;
            
            if (elapsed >= checkInterval) {
                lastTickTime = currentTime;
                tickCount++;
                self.postMessage({ 
                    type: 'TICK', 
                    timestamp: currentTime,
                    tickCount: tickCount
                });
            }
            
            intervalId = setTimeout(check, 100);
        }
        
        check();
    } else if (type === 'STOP') {
        isRunning = false;
        if (intervalId) {
            clearTimeout(intervalId);
            intervalId = null;
        }
    } else if (type === 'SET_INTERVAL') {
        checkInterval = payload.interval || 1000;
    } else if (type === 'PING') {
        self.postMessage({ 
            type: 'PONG', 
            timestamp: Date.now(),
            tickCount: tickCount,
            isRunning: isRunning
        });
    }
};
