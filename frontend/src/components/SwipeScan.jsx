/**
 * Component providing a swipe-activated trigger interface for scanning actions.
 */
import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ScanLine, ChevronUp, ChevronDown } from 'lucide-react';

export default function SwipeScan() {
  const [offsetY, setOffsetY] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const startY = useRef(0);
  const navigate = useNavigate();

  const maxDrag = 65;

  const handlePointerDown = (e) => {
    setIsDragging(true);
    startY.current = e.clientY - offsetY;
    e.target.setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e) => {
    if (!isDragging) return;
    let newY = e.clientY - startY.current;
    
    if (newY > maxDrag) newY = maxDrag;
    if (newY < -maxDrag) newY = -maxDrag;
    
    setOffsetY(newY);
  };

  const handlePointerUp = (e) => {
    if (!isDragging) return;
    setIsDragging(false);
    
    if (offsetY <= -maxDrag + 15) {
      alert("Scanner triggered for Putaway (Cosmetic). Hardware not detected.");
      navigate('/receive');
    } else if (offsetY >= maxDrag - 15) {
      alert("Scanner triggered for Picking (Cosmetic). Hardware not detected.");
      navigate('/pick');
    }
    
    setOffsetY(0);
    e.target.releasePointerCapture(e.pointerId);
  };

  return (
    <div style={{
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center',
      justifyContent: 'space-between',
      background: 'var(--bg-card)', 
      borderRadius: '50px',
      padding: '12px 6px', 
      width: '90px', 
      height: '240px',
      border: '2px solid var(--border-color)', 
      position: 'relative',
      margin: 'auto 0',
      userSelect: 'none', 
      touchAction: 'none',
      boxShadow: 'inset 0 4px 6px rgba(0,0,0,0.05)'
    }}>
      
      <div style={{ 
        color: offsetY < -20 ? 'var(--accent-primary)' : 'var(--text-muted)', 
        fontSize: '0.75rem', 
        fontWeight: '700', 
        display: 'flex', 
        flexDirection: 'column', 
        alignItems: 'center',
        transition: 'color 0.2s',
        opacity: 0.8
      }}>
        <ChevronUp size={20} style={{ marginBottom: '-2px' }} />
        Putaway
      </div>
      
      <div 
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        style={{
          width: '74px', 
          height: '74px', 
          borderRadius: '50%',
          background: 'var(--success)', 
          color: 'white',
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center',
          cursor: isDragging ? 'grabbing' : 'grab',
          transform: `translateY(${offsetY}px)`,
          position: 'absolute', 
          top: 'calc(50% - 37px)',
          boxShadow: '0 6px 12px rgba(22, 163, 74, 0.4)',
          transition: isDragging ? 'none' : 'transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)',
          zIndex: 10
        }}
      >
        <ScanLine size={36} />
      </div>

      <div style={{ 
        color: offsetY > 20 ? 'var(--warning)' : 'var(--text-muted)', 
        fontSize: '0.75rem', 
        fontWeight: '700', 
        display: 'flex', 
        flexDirection: 'column', 
        alignItems: 'center',
        transition: 'color 0.2s',
        opacity: 0.8
      }}>
        Pick
        <ChevronDown size={20} style={{ marginTop: '-2px' }} />
      </div>
      
    </div>
  );
}
