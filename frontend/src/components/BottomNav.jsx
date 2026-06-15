/**
 * Bottom navigation component managing route transitions and gesture-based scanner activation.
 */
import React, { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { LayoutDashboard, PackagePlus, PackageMinus, EyeOff, Lock } from 'lucide-react';
import { useUI } from '../App';
import { useAuth } from '../AuthContext';

export default function BottomNav() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isTableVisible, setIsTableVisible } = useUI();
  const { canPutaway, canPick } = useAuth();
  
  const getPosFromRoute = (pathname) => {
    if (pathname === '/receive') return -1;
    if (pathname === '/pick') return 1;
    return 0;
  };

  const [posIndex, setPosIndex] = useState(getPosFromRoute(location.pathname));
  const [isDragging, setIsDragging] = useState(false);
  const [dragOffset, setDragOffset] = useState(0);
  const [dragOffsetY, setDragOffsetY] = useState(0);
  
  const startX = useRef(0);
  const startY = useRef(0);
  const containerRef = useRef(null);
  
  const trackWidth = 360; 
  const slotWidth = trackWidth / 3; 
  
  useEffect(() => {
    setPosIndex(getPosFromRoute(location.pathname));
  }, [location.pathname]);

  const handlePointerDown = (e) => {
    setIsDragging(true);
    startX.current = e.clientX - dragOffset;
    startY.current = e.clientY - dragOffsetY;
    e.target.setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e) => {
    if (!isDragging) return;
    let newX = e.clientX - startX.current;
    let newY = e.clientY - startY.current;
    
    if (posIndex === -1 && newX < 0) newX = 0; 
    if (posIndex === 1 && newX > 0) newX = 0; 
    
    const absoluteX = (posIndex * slotWidth) + newX;
    if (absoluteX < -slotWidth) newX = -slotWidth - (posIndex * slotWidth);
    if (absoluteX > slotWidth) newX = slotWidth - (posIndex * slotWidth);
    
    if (newY > 0) newY = 0;
    if (newY < -80) newY = -80; 
    
    setDragOffset(newX);
    setDragOffsetY(newY);
  };

  const handlePointerUp = (e) => {
    if (!isDragging) return;
    setIsDragging(false);
    
    if (dragOffsetY <= -50) {
      if (posIndex === -1) alert("Scanner triggered for Putaway (Cosmetic). Hardware not detected.");
      else if (posIndex === 1) alert("Scanner triggered for Picking (Cosmetic). Hardware not detected.");
    }

    const absoluteX = (posIndex * slotWidth) + dragOffset;
    let newPosIndex = 0;
    if (absoluteX <= -(slotWidth / 2)) newPosIndex = -1;
    else if (absoluteX >= (slotWidth / 2)) newPosIndex = 1;

    if (newPosIndex === -1 && !canPutaway) newPosIndex = 0;
    if (newPosIndex === 1 && !canPick) newPosIndex = 0;

    setDragOffset(0); 
    setDragOffsetY(0);
    
    if (newPosIndex !== posIndex) {
      setPosIndex(newPosIndex);
      if (newPosIndex === -1) navigate('/receive');
      else if (newPosIndex === 1) navigate('/pick');
      else navigate('/');
    }
    
    e.target.releasePointerCapture(e.pointerId);
  };

  const handleButtonPointerDown = (e, targetPos) => {
    e.target.setPointerCapture(e.pointerId);
    e.target.dataset.startY = e.clientY;
    e.target.dataset.swipedUp = 'false';
  };

  const handleButtonPointerMove = (e) => {
    if (!e.target.dataset.startY) return;
    const start = parseFloat(e.target.dataset.startY);
    const diffY = e.clientY - start;
    if (diffY < -30) e.target.dataset.swipedUp = 'true';
  };

  const handleButtonPointerUp = (e, targetPos) => {
    if (!e.target.dataset.startY) return;
    const isSwipedUp = e.target.dataset.swipedUp === 'true';
    e.target.dataset.startY = '';
    e.target.dataset.swipedUp = 'false';
    e.target.releasePointerCapture(e.pointerId);

    if (isSwipedUp) {
      if (targetPos === -1) alert("Scanner triggered for Putaway (Cosmetic). Hardware not detected.");
      else if (targetPos === 1) alert("Scanner triggered for Picking (Cosmetic). Hardware not detected.");
    } else {
      handleSlotClick(targetPos);
    }
  };

  const handleSlotClick = (targetPos) => {
    if (isDragging) return;
    if (targetPos === 0) {
      if (location.pathname === '/') setIsTableVisible(!isTableVisible);
      else navigate('/');
    } else if (targetPos === -1 && canPutaway) {
      navigate('/receive');
    } else if (targetPos === 1 && canPick) {
      navigate('/pick');
    }
  };

  const currentTranslateX = (posIndex * slotWidth) + (isDragging ? dragOffset : 0);
  const currentTranslateY = isDragging ? dragOffsetY : 0;

  return (
    <div style={{
      position: 'fixed',
      bottom: '30px',
      left: '50%',
      transform: 'translateX(-50%)',
      zIndex: 1000,
      background: 'rgba(255, 255, 255, 0.85)',
      backdropFilter: 'blur(10px)',
      border: '1px solid var(--border-color)',
      borderRadius: '50px',
      boxShadow: '0 15px 35px rgba(0,0,0,0.1)',
      display: 'flex',
      alignItems: 'center',
      width: `${trackWidth}px`,
      height: '80px',
      padding: '0',
      userSelect: 'none',
      touchAction: 'none' 
    }}>
      
      <div 
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        style={{
          position: 'absolute',
          left: `calc(50% - ${slotWidth / 2}px)`,
          width: `${slotWidth}px`,
          height: '100%',
          background: 'var(--accent-primary)',
          borderRadius: '50px',
          boxShadow: '0 4px 15px rgba(37, 99, 235, 0.4)',
          transform: `translate(${currentTranslateX}px, ${currentTranslateY}px)`,
          transition: isDragging ? 'none' : 'transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)',
          cursor: isDragging ? 'grabbing' : 'grab',
          zIndex: 1
        }}
      />

      <div 
        onPointerDown={canPutaway ? (e) => handleButtonPointerDown(e, -1) : undefined}
        onPointerMove={canPutaway ? handleButtonPointerMove : undefined}
        onPointerUp={canPutaway ? (e) => handleButtonPointerUp(e, -1) : undefined}
        onPointerCancel={canPutaway ? (e) => handleButtonPointerUp(e, -1) : undefined}
        style={{
          flex: 1, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          color: posIndex === -1 ? 'white' : 'var(--text-secondary)',
          zIndex: 2, cursor: canPutaway ? 'pointer' : 'not-allowed', transition: 'color 0.3s',
          fontWeight: posIndex === -1 ? 'bold' : 'normal',
          opacity: canPutaway ? 1 : 0.5
        }}
      >
        {canPutaway ? <PackagePlus size={24} style={{ pointerEvents: 'none' }} /> : <Lock size={24} style={{ pointerEvents: 'none' }} />}
        <span style={{ fontSize: '0.75rem', marginTop: '4px', pointerEvents: 'none' }}>{canPutaway ? 'Putaway' : 'Locked'}</span>
      </div>

      <div 
        onClick={() => handleSlotClick(0)}
        style={{
          flex: 1, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          color: posIndex === 0 ? 'white' : 'var(--text-secondary)',
          zIndex: 2, cursor: 'pointer', transition: 'color 0.3s',
          fontWeight: posIndex === 0 ? 'bold' : 'normal'
        }}
      >
        {posIndex === 0 && !isTableVisible ? <EyeOff size={24} style={{ pointerEvents: 'none' }} /> : <LayoutDashboard size={24} style={{ pointerEvents: 'none' }} />}
        <span style={{ fontSize: '0.75rem', marginTop: '4px', pointerEvents: 'none' }}>{posIndex === 0 && !isTableVisible ? 'Show' : 'Dash'}</span>
      </div>

      <div 
        onPointerDown={canPick ? (e) => handleButtonPointerDown(e, 1) : undefined}
        onPointerMove={canPick ? handleButtonPointerMove : undefined}
        onPointerUp={canPick ? (e) => handleButtonPointerUp(e, 1) : undefined}
        onPointerCancel={canPick ? (e) => handleButtonPointerUp(e, 1) : undefined}
        style={{
          flex: 1, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          color: posIndex === 1 ? 'white' : 'var(--text-secondary)',
          zIndex: 2, cursor: canPick ? 'pointer' : 'not-allowed', transition: 'color 0.3s',
          fontWeight: posIndex === 1 ? 'bold' : 'normal',
          opacity: canPick ? 1 : 0.5
        }}
      >
        {canPick ? <PackageMinus size={24} style={{ pointerEvents: 'none' }} /> : <Lock size={24} style={{ pointerEvents: 'none' }} />}
        <span style={{ fontSize: '0.75rem', marginTop: '4px', pointerEvents: 'none' }}>{canPick ? 'Pick' : 'Locked'}</span>
      </div>

    </div>
  );
}
