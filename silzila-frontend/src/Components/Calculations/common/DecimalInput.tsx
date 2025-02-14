import React from 'react'

export const DecimalInput = ({
    inputValue,
    autoFocus,

    setInputValue,
    setInputFocused,
}: {
    inputValue: number,
    autoFocus: boolean,

    setInputValue: React.Dispatch<React.SetStateAction<number>>,
    setInputFocused: React.Dispatch<React.SetStateAction<boolean>>,
}) => {
    return (
        <input
            onFocus={() => setInputFocused(true)}
            onBlur={() => setInputFocused(false)}
            autoFocus={autoFocus}
            min={1}
            style={{
                height: "1.5rem",
                lineHeight: "1rem",
                width: "100%",
                margin: "0 4px",
                marginRight: "5px",
                paddingRight: "5px",
                border: 'none',
                outline: 'none',
                color: "rgb(128, 128, 128)"
            }}
            type="number"
            value={inputValue}
            onChange={e => { setInputValue(Number(e.target.value)) }}
        />
    )
}