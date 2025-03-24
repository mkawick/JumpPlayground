using System;
using System.ComponentModel;
using SRDebugger;
using SRF.Service;
using UnityEngine;
using UnityEngine.Scripting;
using KinematicCharacterControllerNamespace;

public class SRDebugOptions : MonoBehaviour
{
    bool _someInternalField;
    int _someInternalField2;
    private float _myRangeProperty = 0f;
    [SerializeField] TinyWizCharacterController TinyWizCharacterController;
    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        // Create a read-only option
        var option01 = OptionDefinition.Create("My Option", () => _someInternalField);
        SRDebug.Instance.AddOption(option01);

        var runSpeedOption = OptionDefinition.Create(
            "Run speed",
            () => TinyWizCharacterController.MaxStableMoveSpeed,
            (newValue) => TinyWizCharacterController.MaxStableMoveSpeed = newValue
        );
        var jumpVertOption = OptionDefinition.Create(
            "Jump strength",
            () => TinyWizCharacterController.JumpUpSpeed,
            (newValue) => TinyWizCharacterController.JumpUpSpeed = newValue
        );
        var airSpeedOption = OptionDefinition.Create(
            "Air speed",
            () => TinyWizCharacterController.MaxAirMoveSpeed,
            (newValue) => TinyWizCharacterController.MaxAirMoveSpeed = newValue
        );
        var gravityOption = OptionDefinition.Create(
            "Gravity strength",
            () => TinyWizCharacterController.Gravity.y,
            (newValue) => TinyWizCharacterController.Gravity = new Vector3(0, newValue, 0)
        );

        SRDebug.Instance.AddOption(runSpeedOption);
        SRDebug.Instance.AddOption(jumpVertOption);
        SRDebug.Instance.AddOption(airSpeedOption);
        SRDebug.Instance.AddOption(gravityOption);

    }

    [NumberRange(0, 10)]
    [Category("My Category")]
    public float MyRangeProperty
    {
        get { return _myRangeProperty; }
        set { _myRangeProperty = value; }
    }

    // Update is called once per frame
    void Update()
    {
        
    }
}
